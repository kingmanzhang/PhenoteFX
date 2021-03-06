package org.monarchinitiative.phenotefx.gui.main;

/*
 * #%L
 * PhenoteFX
 * %%
 * Copyright (C) 2017 Peter Robinson
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import javafx.util.Callback;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.scene.control.*;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.monarchinitiative.phenol.formats.hpo.HpoOnsetTermIds;
import org.monarchinitiative.phenol.formats.hpo.HpoOntology;
import org.monarchinitiative.phenol.ontology.data.Term;
import org.monarchinitiative.phenol.ontology.data.TermId;
import org.monarchinitiative.phenotefx.exception.PhenoteFxException;
import org.monarchinitiative.phenotefx.gui.*;
import org.monarchinitiative.phenotefx.gui.annotationcheck.AnnotationCheckFactory;
import org.monarchinitiative.phenotefx.gui.editrow.EditRowFactory;
import org.monarchinitiative.phenotefx.gui.help.HelpViewFactory;
import org.monarchinitiative.phenotefx.gui.logviewer.LogViewerFactory;
import org.monarchinitiative.phenotefx.gui.newitem.NewItemFactory;
import org.monarchinitiative.phenotefx.gui.progresspopup.ProgressPopup;
import org.monarchinitiative.phenotefx.gui.settings.SettingsViewFactory;
import org.monarchinitiative.phenotefx.io.*;
import org.monarchinitiative.phenotefx.model.*;
import org.monarchinitiative.phenotefx.validation.*;
import com.github.monarchinitiative.hpotextmining.HPOTextMining;
import com.github.monarchinitiative.hpotextmining.TextMiningResult;
import com.github.monarchinitiative.hpotextmining.model.PhenotypeTerm;
import org.monarchinitiative.phenotefx.worker.TermLabelUpdater;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;


/**
 * Created by robinp on 5/22/17.
 * Main presenter for the HPO Phenote App.
 *
 * @author <a href="mailto:peter.robinson@jax.org">Peter Robinson</a>
 * @version 0.2.5 (2018-05-12)
 */
public class PhenotePresenter implements Initializable {
    private static final Logger logger = LogManager.getLogger();
    private static final String settingsFileName = "phenotefx.settings";

    private static final String HP_OBO_URL = "https://raw.githubusercontent.com/obophenotype/human-phenotype-ontology/master/hp.obo";
    private static final String MEDGEN_URL = "ftp://ftp.ncbi.nlm.nih.gov/pub/medgen/MedGen_HPO_OMIM_Mapping.txt.gz";
    private static final String MEDGEN_BASENAME = "MedGen_HPO_OMIM_Mapping.txt.gz";
    private static final String EMPTY_STRING = "";

    @FXML
    private AnchorPane anchorpane;
    @FXML
    private TextField diseaseNameTextField;
    @FXML
    private TextField hpoNameTextField;
    @FXML
    private Label diseaseIDlabel;
    /* ------ MENU ---------- */
    @FXML
    private MenuItem openFileMenuItem;
    @FXML
    private MenuItem exitMenuItem;
    @FXML
    private MenuItem closeMenuItem;
    @FXML
    private MenuItem saveAsMenuItem;
    @FXML
    private ChoiceBox<String> ageOfOnsetChoiceBox;
    @FXML
    private RadioButton IEAbutton;
    @FXML
    private RadioButton ICEbutton;
    @FXML
    private RadioButton PCSbutton;
    @FXML
    private RadioButton TASbutton;
    @FXML
    private TextField frequencyTextField;
    @FXML
    private ChoiceBox<String> frequencyChoiceBox;
    @FXML
    private TextField modifiertextField;
    @FXML
    private TextField descriptiontextField;
    /**
     * The publication (source) for the annotation (refered to as "pub" in the small files).
     */
    @FXML
    private TextField pubTextField;
    @FXML
    private CheckBox notBox;
    @FXML
    private Label lastSourceLabel;
    @FXML
    private CheckBox lastSourceBox;

    private ToggleGroup evidenceGroup;

    private StringProperty diseaseName, diseaseID;

    private Settings settings = null;

    private Map<String, String> omimName2IdMap;

    private Map<String, String> hponame2idMap;

    private Map<String, String> hpoModifer2idMap;

    private Map<String, String> hpoSynonym2LabelMap;

    private HPOOnset hpoOnset;
    /**
     * Is there unsaved work?
     */
    private boolean dirty = false;
    /**
     * Reference to the primary stage of the application.
     */
    private Stage primaryStage = null;

    /**
     * Ontology used by Text-mining widget. Instantiated at first click in {@link #fetchTextMining()}
     */
    private static HpoOntology ontology;

    private ontologizer.ontology.Ontology ontologizerOntology;

    private Frequency frequency;
    /**
     * Header of the current Phenote file.
     */
    private String header = null;
    /**
     * Base name of the current Phenote file
     */
    private String currentPhenoteFileBaseName = null;

    private String currentPhenoteFileFullPath = null;
    /**
     * The last source used, e.g., a PMID (use this to avoid having to re-enter the source)
     */
    private final StringProperty lastSource = new SimpleStringProperty("");
    /**
     * This is the table where the phenotype data will be shown.
     */
    @FXML
    private TableView<PhenoRow> table = null;
    @FXML
    private TableColumn<PhenoRow, String> diseaseIDcol;
    @FXML
    private TableColumn<PhenoRow, String> diseaseNamecol;
    @FXML
    private TableColumn<PhenoRow, String> phenotypeNameCol;
    @FXML
    private TableColumn<PhenoRow, String> ageOfOnsetNamecol;
    @FXML
    private TableColumn<PhenoRow, String> frequencyCol;
    @FXML
    private TableColumn<PhenoRow, String> sexCol;
    @FXML
    private TableColumn<PhenoRow, String> negationCol;
    @FXML
    private TableColumn<PhenoRow, String> modifierCol;
    @FXML
    private TableColumn<PhenoRow, String> descriptionCol;
    @FXML
    private TableColumn<PhenoRow, String> pubCol;
    @FXML
    private TableColumn<PhenoRow, String> evidencecol;
    @FXML
    private TableColumn<PhenoRow, String> biocurationCol;

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        loadSettings();
        boolean ready = checkReadiness();
        setDefaultHeader();
        if (!ready) {
            return;
        }
        inputHPOandMedGen();
        setupAutocomplete();

        anchorpane.setPrefSize(1400, 1000);
        setUpTable();
        table.setItems(getRows());
        // set up buttons
        exitMenuItem.setOnAction(e -> exitGui());
        openFileMenuItem.setOnAction(this::openPhenoteFile);

        this.diseaseNameTextField.setPromptText("Will default to disease name in first row if left empty");
        this.hpoNameTextField.setPromptText("Enter preferred label or synonym (will be automatically converted)");

        evidenceGroup = new ToggleGroup();
        IEAbutton.setToggleGroup(evidenceGroup);
        ICEbutton.setToggleGroup(evidenceGroup);
        PCSbutton.setToggleGroup(evidenceGroup);
        TASbutton.setToggleGroup(evidenceGroup);
        IEAbutton.setSelected(true);
        hpoOnset = HPOOnset.factory();
        ageOfOnsetChoiceBox.setItems(hpoOnset.getOnsetTermList());
        this.frequency = Frequency.factory();
        frequencyChoiceBox.setItems(frequency.getFrequencyTermList());
        this.descriptiontextField.setPromptText("free text description of anything not captured with standards (optional)");
        this.pubTextField.setPromptText("Source of assertion (usually PubMed, OMIM, Orphanet...)");
        this.frequencyTextField.setPromptText("A value such as 7/13 or 54% (leave empty if pulldown used)");
        this.diseaseIDlabel.setTooltip(new Tooltip("Name of a disease (OMIM IDs will be automatically populated)"));
        this.modifiertextField.setPromptText("Autocomplete label of HPO modifier term");
        /* The following removes whitespace if the user pastes in a PMID */
        pubTextField.textProperty().addListener( // ChangeListener
                (observable, oldValue, newValue) -> {
                    String txt = pubTextField.getText();
                    txt = txt.replaceAll("\\s", "");
                    pubTextField.setText(txt);
                });

        this.lastSourceLabel.textProperty().bind(this.lastSource);
        setUpKeyAccelerators();
    }

    /**
     * Add short cuts to the menu items. Note--adding accelerator="Shortcut+M" to the fxml is portable across
     * Mac and Windows and Linux.
     */
    private void setUpKeyAccelerators() {
        this.saveAsMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.SHIFT_DOWN, KeyCombination.META_DOWN));
        this.closeMenuItem.setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.META_DOWN));
    }

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }

    /**
     * When we create a new annotation file,
     * we need to set the Header line here.
     */
    private void setDefaultHeader() {
        this.header = SmallfileParser.getStandardHeaderLine();
    }

    /**
     * Called by the initialize method. Serves to set up the
     * Maps with HPO and Disease name information for the autocompletes.
     */
    private void inputHPOandMedGen() {
        MedGenParser medGenParser = new MedGenParser();
        omimName2IdMap = medGenParser.getOmimName2IdMap();
        try {
            HPOParser parser2 = new HPOParser();
            ontology = parser2.getHpoOntology();
            hponame2idMap = parser2.getHpoName2IDmap();
            hpoSynonym2LabelMap = parser2.getHpoSynonym2PreferredLabelMap();
            this.hpoModifer2idMap = parser2.getModifierMap();
        } catch (Exception e) {
            int ln = Thread.currentThread().getStackTrace()[1].getLineNumber();
            String msg = String.format("Could not parse ontology file [PhenotePresenter line %d]: %s", ln, e.toString());
            logger.error(msg);
            ErrorDialog.displayException("Error", msg, e);
        }
        logger.trace("Done input HPO/MedGen");
    }

    /**
     * Checks if the HPO and medgen files have been downloaded already, and if
     * not shows an alert window.
     */
    private boolean checkReadiness() {
        StringBuffer sb = new StringBuffer();
        boolean ready = true;
        boolean hpoready = org.monarchinitiative.phenotefx.gui.Platform.checkHPOFileDownloaded();
        if (!hpoready) {
            sb.append("HPO File not found. ");
            ready = false;
        }
        boolean medgenready = org.monarchinitiative.phenotefx.gui.Platform.checkMedgenFileDownloaded();
        if (!medgenready) {
            sb.append("MedGen_HPO_OMIM_Mapping.txt.gz not found. ");
            ready = false;
        }
        if (!ready) {
            sb.append("You need to download the files before working with annotation data.");
            Task<Void> task = new Task<Void>() {
                @Override
                protected Void call() {
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("Warning");
                    alert.setHeaderText(sb.toString());
                    alert.setContentText("Download the files with the commands in the Setup menu! Then restart this app");
                    alert.showAndWait();

                    return null;
                }
            };
            task.run();
        }
        return ready;
    }


    /**
     * Write the settings from the current session to file and exit.
     */
    @FXML
    private void exitGui() {
        saveSettings();
        javafx.application.Platform.exit();
    }


    private static void showAlert(String message) {
        Alert a = new Alert(Alert.AlertType.ERROR);
        a.setTitle("Error occured");
        a.setHeaderText(null);
        a.setContentText(message);
        a.showAndWait();
        a.show();
    }

    /**
     * Parse XML file from standard location and return as {@link Settings} bean.
     */
    private void loadSettings() {
        File defaultSettingsPath = new File(org.monarchinitiative.phenotefx.gui.Platform.getPhenoteFXDir().getAbsolutePath()
                + File.separator + settingsFileName);
        if (!org.monarchinitiative.phenotefx.gui.Platform.getPhenoteFXDir().exists()) {
            File fck = new File(org.monarchinitiative.phenotefx.gui.Platform.getPhenoteFXDir().getAbsolutePath());
            if (!fck.mkdir()) { // make sure config directory is created, exit if not
                showAlert("Unable to create HRMD-gui config directory.");
            }
        }
        if (!defaultSettingsPath.exists()) {
            this.settings = new Settings();
            return; // create blank new Settings
        }
        this.settings = Settings.factory(defaultSettingsPath.getAbsolutePath());
    }

    /**
     * This method gets called when user chooses to close Gui. Content of
     * {@link Settings} bean is dumped
     * in XML format to platform-dependent default location.
     */
    private void saveSettings() {
        File hrmdDirectory = org.monarchinitiative.phenotefx.gui.Platform.getPhenoteFXDir();
        File parentDir = hrmdDirectory.getParentFile();
        if (!parentDir.exists()) {
            if (!parentDir.mkdir()) {
                showAlert("Error saving settings. Settings not saved.");
                return;
            }
        }
        if (!hrmdDirectory.exists()) {
            try {
                hrmdDirectory.createNewFile();
            } catch (IOException e) {
                showAlert("Error saving settings. Settings not saved.");
                return;
            }
        }
        File settingsFile = new File(hrmdDirectory.getAbsolutePath()
                + File.separator + settingsFileName);
        if (!Settings.saveToFile(settings, settingsFile)) {
            logger.warn("Unable to save settings to file");
        }
    }

    /**
     * Uses the {@link WidthAwareTextFields} class to set up autocompletion for the disease name and the HPO name
     */
    private void setupAutocomplete() {
        if (omimName2IdMap != null) {
            WidthAwareTextFields.bindWidthAwareAutoCompletion(diseaseNameTextField, omimName2IdMap.keySet());
        }
        diseaseNameTextField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.equals("")) {
                diseaseID.setValue("");
            }
        });

        this.diseaseID = new SimpleStringProperty(this, "diseaseID", "");
        this.diseaseName = new SimpleStringProperty(this, "diseaseName", "");
        diseaseIDlabel.textProperty().bindBidirectional(diseaseID);
        diseaseNameTextField.textProperty().bindBidirectional(diseaseName);
        diseaseNameTextField.setOnAction(e -> {
            String name = diseaseName.getValue();
            diseaseID.setValue(omimName2IdMap.get(name));
        });
        if (hpoSynonym2LabelMap != null) {
            WidthAwareTextFields.bindWidthAwareAutoCompletion(hpoNameTextField, hpoSynonym2LabelMap.keySet());
        }

        if (hpoModifer2idMap != null) {
            WidthAwareTextFields.bindWidthAwareAutoCompletion(modifiertextField, hpoModifer2idMap.keySet());
        }
    }

    /**
     * Open a main file ("small file") and populate the table with it.
     */
    private void openPhenoteFile(ActionEvent event) {
        if (dirty) {
            boolean discard = PopUps.getBooleanFromUser("Discard unsaved changes?", "Unsaved work on current annotation file", "Discard unsaved work?");
            if (!discard) return;
        }
        Stage stage = (Stage) this.anchorpane.getScene().getWindow();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open Resource File");
        File f = fileChooser.showOpenDialog(stage);
        if (f != null) {
            logger.trace("Opening file " + f.getAbsolutePath());
            populateTable(f);
        }
    }

    /**
     * Put rows into the table that represent the disease annotations from the file.
     *
     * @param f "small file" with HPO disease annotations.
     */
    private void populateTable(File f) {
        logger.trace(String.format("About to populate the table from file %s", f.getAbsolutePath()));
        List<String> errors = new ArrayList<>();
        setUpTable();
        ObservableList<PhenoRow> phenolist;
        this.currentPhenoteFileBaseName = f.getName();
        this.currentPhenoteFileFullPath = f.getAbsolutePath();
        try {
            SmallfileParser parser = new SmallfileParser(f, ontology);
            phenolist = parser.parse();
            logger.trace(String.format("About to add %d lines to the table", phenolist.size()));
            this.table.setItems(phenolist);

        } catch (PhenoteFxException e) {
            PopUps.showException("Parse error",
                    "Could not parse small file",
                    String.format("Could not parse file %s", f.getAbsolutePath()),
                    e);
            errors.add(e.getMessage());
            this.currentPhenoteFileBaseName = null; // couldnt open this file!
        }
        if (errors.size() > 0) {
            String s = String.join("\n", errors);
            ErrorDialog.display("Error", s);
        }
    }

    /**
     * @return an empty list of {@link PhenoRow} to initialize the table.
     */
    private ObservableList<PhenoRow> getRows() {
        ObservableList<PhenoRow> olist = FXCollections.observableArrayList();
        olist.add(new PhenoRow());
        return olist;
    }


    /**
     * Set up the table and define the behavior of the columns
     */
    private void setUpTable() {
        table.setEditable(true);

        diseaseIDcol.setCellValueFactory(new PropertyValueFactory<>("diseaseID"));
        diseaseIDcol.setCellFactory(TextFieldTableCell.forTableColumn());
        diseaseIDcol.setOnEditCommit(cee -> cee.getTableView().getItems().get(cee.getTablePosition().getRow()).setDiseaseID(cee.getNewValue()));

        diseaseNamecol.setCellValueFactory(new PropertyValueFactory<>("diseaseName"));
        diseaseNamecol.setCellFactory(TextFieldTableCell.forTableColumn());
        diseaseNamecol.setOnEditCommit(cee -> cee.getTableView().getItems().get(cee.getTablePosition().getRow()).setDiseaseName(cee.getNewValue()));

        phenotypeNameCol.setCellValueFactory(new PropertyValueFactory<>("phenotypeName"));
        phenotypeNameCol.setCellFactory(new Callback<TableColumn<PhenoRow, String>, TableCell<PhenoRow, String>>() {
            @Override
            public TableCell<PhenoRow, String> call(TableColumn<PhenoRow, String> p) {
                return new TableCell<PhenoRow, String>() {
                    @Override
                    public void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item == null) {
                            setTooltip(null);
                            setText(null);
                        } else {
                            Tooltip tooltip = new Tooltip();
                            PhenoRow myModel = getTableView().getItems().get(getTableRow().getIndex());
                            tooltip.setText(myModel.getPhenotypeID());
                            setTooltip(tooltip);
                            setText(item);
                        }
                    }
                };
            }
        });
        phenotypeNameCol.setEditable(false);
        phenotypeNameCol.setSortable(true);

        ageOfOnsetNamecol.setCellValueFactory(new PropertyValueFactory<>("onsetName"));
        ageOfOnsetNamecol.setCellFactory(new Callback<TableColumn<PhenoRow, String>, TableCell<PhenoRow, String>>() {
            @Override
            public TableCell<PhenoRow, String> call(TableColumn<PhenoRow, String> p) {
                return new TableCell<PhenoRow, String>() {
                    @Override
                    public void updateItem(String item, boolean empty) {
                        super.updateItem(item, empty);
                        if (item == null) {
                            setTooltip(null);
                            setText(null);
                        } else {
                            Tooltip tooltip = new Tooltip();
                            PhenoRow myModel = getTableView().getItems().get(getTableRow().getIndex());
                            tooltip.setText(myModel.getOnsetID());
                            setTooltip(tooltip);
                            setText(item);
                        }
                    }
                };
            }
        });
        ageOfOnsetNamecol.setEditable(false);

        frequencyCol.setCellValueFactory(new PropertyValueFactory<>("frequency"));
        frequencyCol.setCellFactory(TextFieldTableCell.forTableColumn());
        frequencyCol.setEditable(false);

        sexCol.setCellValueFactory(new PropertyValueFactory<>("sex"));
        sexCol.setCellFactory(TextFieldTableCell.forTableColumn());
        sexCol.setOnEditCommit(event -> event.getTableView().getItems().get(event.getTablePosition().getRow()).setSex(event.getNewValue()));

        negationCol.setCellValueFactory(new PropertyValueFactory<>("negation"));
        negationCol.setCellFactory(TextFieldTableCell.forTableColumn());
        negationCol.setOnEditCommit(event -> {
                    if (NotValidator.isValid(event.getNewValue())) {
                        event.getTableView().getItems().get(event.getTablePosition().getRow()).setNegation(event.getNewValue());
                    }
                    dirty = true;
                    event.getTableView().refresh();
                }
        );

        modifierCol.setCellValueFactory(new PropertyValueFactory<>("modifier"));
        modifierCol.setCellFactory(TextFieldTableCell.forTableColumn());
        modifierCol.setEditable(true);

        descriptionCol.setCellValueFactory(new PropertyValueFactory<>("description"));
        descriptionCol.setCellFactory(TextFieldTableCell.forTableColumn());


        pubCol.setCellValueFactory(new PropertyValueFactory<>("publication"));
        pubCol.setCellFactory(TextFieldTableCell.forTableColumn());


        evidencecol.setCellValueFactory(new PropertyValueFactory<>("evidence"));
        evidencecol.setCellFactory(TextFieldTableCell.forTableColumn());
        evidencecol.setEditable(true);

        biocurationCol.setCellValueFactory(new PropertyValueFactory<>("biocuration"));
        biocurationCol.setCellFactory(TextFieldTableCell.forTableColumn());
        biocurationCol.setOnEditCommit(event -> event.getTableView().getItems().get(event.getTablePosition().getRow()).setBiocuration(event.getNewValue()));

        // The following makes the table only show the defined columns (otherwise, an "extra" column is shown)
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        setUpEvidenceContextMenu();
        setUpPublicationPopupDialog();
        setUpDescriptionPopupDialog();
        setUpSexContextMenu();
        setUpHpoContextMenu();
        setUpOnsetContextMenu();
        setUpNOTContextMenu();
        setUpFrequencyPopupDialog();
    }


    /**
     * Set up the popup of the evidence menu.
     */
    private void setUpEvidenceContextMenu() {
        //enable individual cells to be selected, instead of entire rows, call
        table.getSelectionModel().setCellSelectionEnabled(true);
        // The following sets up a context menu JUST for the evidence column.
        evidencecol.setCellFactory( //Callback
                (column) -> {
                    final TableCell<PhenoRow, String> cell = new TableCell<>();
                    cell.itemProperty().addListener(// ChangeListener
                            (obs, oldValue, newValue) -> {
                                if (newValue != null) {
                                    final ContextMenu cellMenu = new ContextMenu();
                                    MenuItem ieaMenuItem = new MenuItem("IEA");
                                    ieaMenuItem.setOnAction(e -> {
                                        PhenoRow item = (PhenoRow) cell.getTableRow().getItem();
                                        item.setEvidence("IEA");
                                        table.refresh();

                                    });
                                    MenuItem pcsMenuItem = new MenuItem("PCS");
                                    pcsMenuItem.setOnAction(e -> {
                                        PhenoRow item = (PhenoRow) cell.getTableRow().getItem();
                                        item.setEvidence("PCS");
                                        table.refresh();

                                    });
                                    MenuItem tasMenuItem = new MenuItem("TAS");
                                    tasMenuItem.setOnAction(e -> {
                                        PhenoRow item = (PhenoRow) cell.getTableRow().getItem();
                                        item.setEvidence("TAS");
                                        table.refresh();

                                    });
                                    cellMenu.getItems().addAll(ieaMenuItem, pcsMenuItem, tasMenuItem);
                                    cell.setContextMenu(cellMenu);
                                } else {
                                    cell.setContextMenu(null);
                                }

                            });
                    cell.textProperty().bind(cell.itemProperty());
                    return cell;
                });
    }

    /**
     * Allow users to set the NOT (negation) field with a right click
     */
    private void setUpNOTContextMenu() {
        //enable individual cells to be selected, instead of entire rows, call
        table.getSelectionModel().setCellSelectionEnabled(true);
        // The following sets up a context menu JUST for the evidence column.
        negationCol.setCellFactory( // Callback
                (column) -> {
                    final TableCell<PhenoRow, String> cell = new TableCell<>();
                    cell.itemProperty().addListener(// ChangeListener
                            (observableValue, oldValue, newValue) -> {
                                final ContextMenu cellMenu = new ContextMenu();
                                final TableRow<?> row = cell.getTableRow();
                                final ContextMenu rowMenu;
                                if (row != null) {
                                    rowMenu = cell.getTableRow().getContextMenu();
                                    if (rowMenu != null) {
                                        cellMenu.getItems().addAll(rowMenu.getItems());
                                        cellMenu.getItems().add(new SeparatorMenuItem());
                                    } else {
                                        final ContextMenu tableMenu = cell.getTableView().getContextMenu();
                                        if (tableMenu != null) {
                                            cellMenu.getItems().addAll(tableMenu.getItems());
                                            cellMenu.getItems().add(new SeparatorMenuItem());
                                        }
                                    }
                                }
                                MenuItem notMenuItem = new MenuItem("NOT");
                                notMenuItem.setOnAction(e -> {
                                    PhenoRow item = (PhenoRow) cell.getTableRow().getItem();
                                    item.setNegation("NOT");
                                    table.refresh();
                                });
                                MenuItem clearMenuItem = new MenuItem("Clear");
                                clearMenuItem.setOnAction(e -> {
                                    PhenoRow item = (PhenoRow) cell.getTableRow().getItem();
                                    item.setNegation(EMPTY_STRING);
                                    table.refresh();
                                });
                                cellMenu.getItems().addAll(notMenuItem, clearMenuItem);
                                cell.setContextMenu(cellMenu);

                            });
                    cell.textProperty().bind(cell.itemProperty());
                    return cell;
                });
    }


    /**
     * Set up the popup of the evidence menu.
     */
    private void setUpSexContextMenu() {
        //enable individual cells to be selected, instead of entire rows, call
        table.getSelectionModel().setCellSelectionEnabled(true);
        // The following sets up a context menu JUST for the evidence column.
        sexCol.setCellFactory( // Callback
                (column) -> {
                    final TableCell<PhenoRow, String> cell = new TableCell<>();
                    cell.itemProperty().addListener(// ChangeListener
                            (observableValue, oldValue, newValue) -> {
                                if (newValue != null) {
                                    final ContextMenu cellMenu = new ContextMenu();
                                    final TableRow<?> row = cell.getTableRow();
                                    final ContextMenu rowMenu;
                                    if (row != null) {
                                        rowMenu = cell.getTableRow().getContextMenu();
                                        if (rowMenu != null) {
                                            cellMenu.getItems().addAll(rowMenu.getItems());
                                            cellMenu.getItems().add(new SeparatorMenuItem());
                                        } else {
                                            final ContextMenu tableMenu = cell.getTableView().getContextMenu();
                                            if (tableMenu != null) {
                                                cellMenu.getItems().addAll(tableMenu.getItems());
                                                cellMenu.getItems().add(new SeparatorMenuItem());
                                            }
                                        }
                                    }
                                    MenuItem maleMenuItem = new MenuItem("MALE");
                                    maleMenuItem.setOnAction(e -> {
                                        PhenoRow item = (PhenoRow) cell.getTableRow().getItem();
                                        item.setSex("MALE");
                                        table.refresh();
                                    });
                                    MenuItem femaleMenuItem = new MenuItem("FEMALE");
                                    femaleMenuItem.setOnAction(e -> {
                                        PhenoRow item = (PhenoRow) cell.getTableRow().getItem();
                                        item.setSex("FEMALE");
                                        table.refresh();
                                    });
                                    MenuItem clearMenuItem = new MenuItem("Clear");
                                    clearMenuItem.setOnAction(e -> {
                                        PhenoRow item = (PhenoRow) cell.getTableRow().getItem();
                                        item.setSex(EMPTY_STRING);
                                        table.refresh();
                                    });
                                    cellMenu.getItems().addAll(maleMenuItem, femaleMenuItem, clearMenuItem);
                                    cell.setContextMenu(cellMenu);
                                } else {
                                    cell.setContextMenu(null);
                                }
                            });
                    cell.textProperty().bind(cell.itemProperty());
                    return cell;
                });
    }


    /**
     * Set up the popup of the onset menu. If the users acitivates the menu, this updates the data in the
     * corresponding {@link PhenoRow} (annotation) object.
     */
    private void setUpOnsetContextMenu() {
        //enable individual cells to be selected, instead of entire rows, call
        table.getSelectionModel().setCellSelectionEnabled(true);
        // The following sets up a context menu JUST for the evidence column.
        ageOfOnsetNamecol.setCellFactory( // Callback
                (column) -> {
                    final TableCell<PhenoRow, String> cell = new TableCell<>();
                    cell.itemProperty().addListener(// ChangeListener
                            (obs, oldValue, newValue) -> {
                                //if (newValue != null) {
                                final ContextMenu cellMenu = new ContextMenu();
                                final TableRow<PhenoRow> tableRow = cell.getTableRow();
                                final PhenoRow phenoRow = tableRow.getItem();
                                MenuItem anteNatalOnsetItem = new MenuItem("Antenatal onset");
                                anteNatalOnsetItem.setOnAction(e -> {
                                    phenoRow.setOnsetID(HpoOnsetTermIds.ANTENATAL_ONSET.getIdWithPrefix());
                                    phenoRow.setOnsetName("Antenatal onset");
                                    table.refresh();
                                });
                                MenuItem embryonalOnsetItem = new MenuItem("Embryonal onset");
                                embryonalOnsetItem.setOnAction(e -> {
                                    phenoRow.setOnsetID(HpoOnsetTermIds.EMBRYONAL_ONSET.getIdWithPrefix());
                                    phenoRow.setOnsetName("Embryonal onset");
                                    table.refresh();
                                });
                                MenuItem fetalOnsetItem = new MenuItem("Fetal onset");
                                fetalOnsetItem.setOnAction(e -> {
                                    phenoRow.setOnsetID(HpoOnsetTermIds.FETAL_ONSET.getIdWithPrefix());
                                    phenoRow.setOnsetName("Fetal onset");
                                    table.refresh();
                                });
                                MenuItem congenitalOnsetItem = new MenuItem("Congenital onset");
                                congenitalOnsetItem.setOnAction(e -> {
                                    phenoRow.setOnsetID(HpoOnsetTermIds.CONGENITAL_ONSET.getIdWithPrefix());
                                    phenoRow.setOnsetName("Congenital onset");
                                    table.refresh();
                                });
                                MenuItem neonatalOnsetItem = new MenuItem("Neonatal onset");
                                neonatalOnsetItem.setOnAction(e -> {
                                    phenoRow.setOnsetID(HpoOnsetTermIds.NEONATAL_ONSET.getIdWithPrefix());
                                    phenoRow.setOnsetName("Neonatal onset");
                                    table.refresh();
                                });
                                MenuItem infantileOnsetItem = new MenuItem("Infantile onset");
                                infantileOnsetItem.setOnAction(e -> {
                                    phenoRow.setOnsetID(HpoOnsetTermIds.INFANTILE_ONSET.getIdWithPrefix());
                                    phenoRow.setOnsetName("Infantile onset");
                                    table.refresh();
                                });
                                MenuItem childhoodOnsetItem = new MenuItem("Childhood onset");
                                childhoodOnsetItem.setOnAction(e -> {
                                    phenoRow.setOnsetID(HpoOnsetTermIds.CHILDHOOD_ONSET.getIdWithPrefix());
                                    phenoRow.setOnsetName("Childhood onset");
                                    table.refresh();
                                });
                                MenuItem juvenileOnsetItem = new MenuItem("Juvenile onset");
                                juvenileOnsetItem.setOnAction(e -> {
                                    phenoRow.setOnsetID(HpoOnsetTermIds.JUVENILE_ONSET.getIdWithPrefix());
                                    phenoRow.setOnsetName("Juvenile onset");
                                    table.refresh();
                                });
                                MenuItem adultOnsetItem = new MenuItem("Adult onset");
                                adultOnsetItem.setOnAction(e -> {
                                    phenoRow.setOnsetID(HpoOnsetTermIds.ADULT_ONSET.getIdWithPrefix());
                                    phenoRow.setOnsetName("Adult onset");
                                    table.refresh();
                                });
                                MenuItem youngAdultOnsetItem = new MenuItem("Young adult onset");
                                youngAdultOnsetItem.setOnAction(e -> {
                                    phenoRow.setOnsetID(HpoOnsetTermIds.YOUNG_ADULT_ONSET.getIdWithPrefix());
                                    phenoRow.setOnsetName("Young adult onset");
                                    table.refresh();
                                });
                                MenuItem middleAgeOnsetItem = new MenuItem("Middle age onset");
                                middleAgeOnsetItem.setOnAction(e -> {
                                    phenoRow.setOnsetID(HpoOnsetTermIds.MIDDLE_AGE_ONSET.getIdWithPrefix());
                                    phenoRow.setOnsetName("Middle age onset");
                                    table.refresh();
                                });
                                MenuItem lateOnsetItem = new MenuItem("Late onset");
                                lateOnsetItem.setOnAction(e -> {
                                    phenoRow.setOnsetID(HpoOnsetTermIds.LATE_ONSET.getIdWithPrefix());
                                    phenoRow.setOnsetName("Late onset");
                                    table.refresh();
                                });
                                MenuItem clearMenuItem = new MenuItem("Clear");
                                clearMenuItem.setOnAction(e -> {
                                    phenoRow.setOnsetID(EMPTY_STRING);
                                    phenoRow.setOnsetName(EMPTY_STRING);
                                    table.refresh();
                                });
                                cellMenu.getItems().addAll(anteNatalOnsetItem,
                                        embryonalOnsetItem,
                                        fetalOnsetItem,
                                        congenitalOnsetItem,
                                        neonatalOnsetItem,
                                        infantileOnsetItem,
                                        childhoodOnsetItem,
                                        juvenileOnsetItem,
                                        adultOnsetItem,
                                        youngAdultOnsetItem,
                                        middleAgeOnsetItem,
                                        lateOnsetItem,
                                        clearMenuItem);
                                cell.setContextMenu(cellMenu);
//                            } else {
//                                cell.setContextMenu(null);
//                            }
                            });
                    cell.textProperty().bind(cell.itemProperty());
                    return cell;
                });
    }

    private String getNewBiocurationEntry() {
        return String.format("%s[%s]", this.settings.getBioCuratorId(), getDate());
    }


    /**
     * Set up the popup of the evidence menu.
     */
    private void setUpHpoContextMenu() {
        //enable individual cells to be selected, instead of entire rows, call
        table.getSelectionModel().setCellSelectionEnabled(true);
        // The following sets up a context menu JUST for the evidence column.
        phenotypeNameCol.setCellFactory(// Callback
                (column) -> {
                    final TableCell<PhenoRow, String> cell = new TableCell<>();
                    cell.itemProperty().addListener(// ChangeListener
                            (observableValue, oldValue, newValue) -> {
                                if (newValue != null) {
                                    final ContextMenu cellMenu = new ContextMenu();
                                    MenuItem hpoUpdateMenuItem = new MenuItem("Update to current ID(not shown) and name");
                                    hpoUpdateMenuItem.setOnAction(e -> {
                                        PhenoRow item = (PhenoRow) cell.getTableRow().getItem();
                                        String id = item.getPhenotypeID();
                                        if (ontology == null) {
                                            logger.error("Ontology null");
                                            return;
                                        }
                                        org.monarchinitiative.phenol.ontology.data.TermId tid = TermId.constructWithPrefix(id);
                                        try {
                                            Term term = ontology.getTermMap().get(tid);
                                            String label = term.getName();
                                            item.setPhenotypeID(term.getId().getIdWithPrefix());
                                            item.setPhenotypeName(label);
                                            item.setNewBiocurationEntry(getNewBiocurationEntry());
                                        } catch (Exception exc) {
                                            exc.printStackTrace();
                                        }
                                        table.refresh();
                                    });

                                    MenuItem hpoIdMenuItem = new MenuItem("show HPO id of this term");
                                    hpoIdMenuItem.setOnAction(e -> {
                                        PhenoRow item = (PhenoRow) cell.getTableRow().getItem();
                                        String label = item.getPhenotypeName();
                                        String id = item.getPhenotypeID();
                                        if (ontology == null) {
                                            logger.error("Ontology null");
                                            return;
                                        }
                                        org.monarchinitiative.phenol.ontology.data.TermId tid = TermId.constructWithPrefix(id);
                                        try {
                                            String msg = String.format("%s [%s]", label, id);
                                            PopUps.showInfoMessage(msg, "Term Id");
                                        } catch (Exception exc) {
                                            exc.printStackTrace();
                                        }
                                        table.refresh();
                                    });
                                    cellMenu.getItems().addAll(hpoUpdateMenuItem, hpoIdMenuItem);
                                    cell.setContextMenu(cellMenu);
                                } else {
                                    cell.setContextMenu(null);
                                }
                            });
                    cell.textProperty().bind(cell.itemProperty());
                    return cell;
                });

    }

    /**
     * Allow the user to update the publication if they right-click on the publication field.
     */
    private void setUpPublicationPopupDialog() {
        // The following sets up a popup dialog JUST for the publication column.
        pubCol.setCellFactory(// Callback
                (column) -> {
                    final TableCell<PhenoRow, String> cell = new TableCell<>();
                    cell.itemProperty().addListener(// ChangeListener
                            (observableValue, oldValue, newValue) -> {
                                final ContextMenu cellMenu = new ContextMenu();
                                final TableRow<?> row = cell.getTableRow();
                                final ContextMenu rowMenu;
                                MenuItem pubDummyMenuItem = new MenuItem("Update publication");
                                PhenoRow phenoRow = (PhenoRow) cell.getTableRow().getItem();
                                if (phenoRow == null) {
                                    //happens at application start up--we can skip it
                                    return;
                                }
                                pubDummyMenuItem.setOnAction(e -> {
                                            String text = EditRowFactory.showPublicationEditDialog(phenoRow, primaryStage);
                                            if (text != null) {
                                                phenoRow.setPublication(text);
                                                phenoRow.setNewBiocurationEntry(getNewBiocurationEntry());
                                                table.refresh();
                                            }
                                        }
                                );
                                cellMenu.getItems().addAll(pubDummyMenuItem);
                                cell.setContextMenu(cellMenu);
                            });
                    cell.textProperty().bind(cell.itemProperty());
                    return cell;
                });
    }


    /**
     * Allow the user to update the publication if they right-click on the publication field.
     */
    private void setUpDescriptionPopupDialog() {
        // The following sets up a popup dialog JUST for the Description column.
        descriptionCol.setCellFactory( // Callback
                (column) -> {
                    final TableCell<PhenoRow, String> cell = new TableCell<>();
                    cell.itemProperty().addListener(// ChangeListener
                            (observableValue, oldValue, newValue) -> {
                                final ContextMenu cellMenu = new ContextMenu();
                                final TableRow<PhenoRow> tableRow = cell.getTableRow();
                                final PhenoRow phenoRow = tableRow.getItem();
                                if (phenoRow == null) {
                                    return; // happens during initial population of table
                                }
                                MenuItem updateDescriptionMenuItem = new MenuItem("Update description");
                                updateDescriptionMenuItem.setOnAction(e -> {
                                            String text = EditRowFactory.showDescriptionEditDialog(phenoRow, primaryStage);
                                            if (text != null) {
                                                phenoRow.setDescription(text);
                                                phenoRow.setNewBiocurationEntry(getNewBiocurationEntry());
                                                table.refresh();
                                            }
                                        }
                                );
                                MenuItem clearDescriptionMenuItem = new MenuItem("Clear");
                                clearDescriptionMenuItem.setOnAction(e -> {
                                    phenoRow.setDescription(EMPTY_STRING);
                                    table.refresh();
                                });
                                cellMenu.getItems().addAll(updateDescriptionMenuItem, clearDescriptionMenuItem);
                                cell.setContextMenu(cellMenu);
                            });
                    cell.textProperty().bind(cell.itemProperty());
                    return cell;
                });
    }


    /**
     * Allow the user to update the publication if they right-click on the publication field.
     */
    private void setUpFrequencyPopupDialog() {
        // The following sets up a popup dialog JUST for the publication column.
        frequencyCol.setCellFactory(// Callback
                (col) -> {
                    final TableCell<PhenoRow, String> cell = new TableCell<>();
                    cell.itemProperty().addListener( // ChangeListener
                            (observableValue, oldValue, newValue) -> {
                                final ContextMenu cellMenu = new ContextMenu();
                                final TableRow<PhenoRow> tableRow = cell.getTableRow();
                                final PhenoRow phenoRow = tableRow.getItem();
                                MenuItem updateFrequencyMenuItem = new MenuItem("Update frequency");
                                if (phenoRow == null) {
                                    //PopUps.showInfoMessage("Could not get reference to table row; consider restart","error");
                                    return;
                                }
                                updateFrequencyMenuItem.setOnAction(e -> {
                                            String text = EditRowFactory.showFrequencyEditDialog(phenoRow);
                                            if (text != null) {
                                                phenoRow.setFrequency(text);
                                                phenoRow.setNewBiocurationEntry(getNewBiocurationEntry());
                                                table.refresh();
                                            }
                                        }
                                );
                                MenuItem clearFrequencyMenuItem = new MenuItem("Clear");
                                clearFrequencyMenuItem.setOnAction(e -> {
                                    phenoRow.setFrequency(EMPTY_STRING);
                                    phenoRow.setNewBiocurationEntry(getNewBiocurationEntry());
                                    table.refresh();
                                });
                                cellMenu.getItems().addAll(updateFrequencyMenuItem, clearFrequencyMenuItem);
                                cell.setContextMenu(cellMenu);
                            });
                    cell.textProperty().bind(cell.itemProperty());
                    return cell;
                });
    }


    /**
     * This is called from the Edit menu and allows the user to import a local copy of
     * hp.obo (usually because the local copy is newer than the official release version of hp.obo).
     *
     * @param e event
     */
    @FXML
    private void importLocalHpObo(ActionEvent e) {
        e.consume();
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import local hp.obo file");
        FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("HPO OBO file (*.obo)", "*.obo");
        chooser.getExtensionFilters().add(extFilter);
        File f = chooser.showOpenDialog(null);
        if (f == null) {
            logger.error("Unable to obtain path to local HPO OBO file");
            PopUps.showInfoMessage("Unable to obtain path to local HPO OBO file", "Error");
            return;
        }
        String hpoOboPath = f.getAbsolutePath();
        try {
            HPOParser parser = new HPOParser(hpoOboPath);
            hponame2idMap = parser.getHpoName2IDmap();
            hpoSynonym2LabelMap = parser.getHpoSynonym2PreferredLabelMap();
            setupAutocomplete();
        } catch (Exception ex) {
            ex.printStackTrace();
            logger.error("Unable to parse local HPO OBO file");
            PopUps.showException("Error", "Unable to parse local hp.obo file", ex.getMessage(), ex);
        }
    }


    /**
     * Get path to the .phenotefx directory, download the file, and if successful
     * set the path to the file in the settings.
     */
    public void downloadHPO(ActionEvent event) {
        ProgressPopup ppopup = new ProgressPopup("HPO download", "downloading hp.obo...");
        ProgressIndicator progressIndicator = ppopup.getProgressIndicator();
        String basename = "hp.obo";
        File dir = Platform.getPhenoteFXDir();
        Downloader downloadTask = new Downloader(dir.getAbsolutePath(), HP_OBO_URL, basename, progressIndicator);
        downloadTask.setOnSucceeded(e -> {
            String abspath = (new File(dir.getAbsolutePath() + File.separator + basename)).getAbsolutePath();
            logger.trace("Setting hp.obo path to " + abspath);
            saveSettings();
            this.settings.setHpoFile(abspath);
            ppopup.close();
        });
        downloadTask.setOnFailed(e -> {
            logger.error("Download of hp.obo failed");
            PopUps.showInfoMessage("Download of hp.obo failed", "Error");
            ppopup.close();
        });
        ppopup.startProgress(downloadTask);
        event.consume();
    }

    /**
     * Download the medgen file to the .phenotefx directory, and if successful
     * set the path to the file in the settings.table.getItems().add(row);
     */
    public void downloadMedGen() {
        ProgressPopup ppopup = new ProgressPopup("Medgen download", String.format("downloading %s...", MEDGEN_BASENAME));
        ProgressIndicator progressIndicator = ppopup.getProgressIndicator();
        File dir = Platform.getPhenoteFXDir();
        Downloader downloadTask = new Downloader(dir.getAbsolutePath(), MEDGEN_URL, MEDGEN_BASENAME, progressIndicator);
        downloadTask.setOnSucceeded(e -> {
            String abspath = (new File(dir.getAbsolutePath() + File.separator + MEDGEN_BASENAME)).getAbsolutePath();
            logger.trace(String.format("Setting %s path to %s", MEDGEN_BASENAME, abspath));
            saveSettings();
            this.settings.setMedgenFile(abspath);
            ppopup.close();
        });
        downloadTask.setOnFailed(e -> {
            logger.error(String.format("Download of %s failed", MEDGEN_BASENAME));
            PopUps.showInfoMessage(String.format("Download of %s failed", MEDGEN_BASENAME), "Error");
            ppopup.close();
        });

        ppopup.startProgress(downloadTask);

    }

    /**
     * This function intends to set all of the disease names to the name in the text field.
     * We can use this to correct the disease names for legacy files where we are using multiple different
     * disease names. Or in cases that the canonical name was updated. If the textfield is empty, the function
     * quietly does nothing. It assumes that the diseaseID is correct and does not try to change that.
     */
    public void setAllDiseasesNames() {
        List<PhenoRow> phenorows = table.getItems();
        String diseaseName = diseaseNameTextField.getText();
        if (diseaseName == null) {
            return;
        }
        for (PhenoRow pr : phenorows) {
            pr.setDiseaseName(diseaseName);
        }
        table.refresh();
    }


    @FXML
    private void updateAllOutdatedTermLabels(ActionEvent e) {
        System.out.println("Updating outdated labels");
        String smallfilepath = settings.getDefaultDirectory();
        if (ontology == null) {
            inputHPOandMedGen();
        }
        TermLabelUpdater updater = new TermLabelUpdater(smallfilepath, ontology);
        updater.replaceOutOfDateLabels();
    }


    /**
     * This method adds one text-mined annotation as a row in the PhenoteFX table.
     *
     * @param hpoid     ID of newly added annotation
     * @param hpoLabel  term label of newly added annotation
     * @param pmid      PubMed id supporting annotation
     * @param isNegated if true, this is a NOT annotation.
     */
    private void addTextMinedAnnotation(String hpoid, String hpoLabel, String pmid, boolean isNegated) {
        PhenoRow textMinedRow = new PhenoRow();
        textMinedRow.setPhenotypeName(hpoLabel);
        textMinedRow.setPhenotypeID(hpoid);

        if (pmid == null || pmid.length() == 0) {
            PopUps.showInfoMessage("Warning-attempting to update annotation without valid PMID", "PubMed Id malformed");
            return;
        }

        if (!pmid.startsWith("PMID"))
            pmid = String.format("PMID:%s", pmid);
        textMinedRow.setPublication(pmid);
        if (isNegated) {
            textMinedRow.setNegation("NOT");
        }
        textMinedRow.setEvidence("PCS");

        String biocuration = String.format("%s[%s]", this.settings.getBioCuratorId(), getDate());


        textMinedRow.setBiocuration(biocuration);
        /* If there is data in the table already, use it to fill in the disease ID and Name. */
        List<PhenoRow> phenorows = table.getItems();
        if (phenorows != null && phenorows.size() > 0) {
            PhenoRow firstrow = phenorows.get(0);
            textMinedRow.setDiseaseName(firstrow.getDiseaseName());
            textMinedRow.setDiseaseID(firstrow.getDiseaseID());
        }
        /* These annotations will always be PMIDs, so we use the code PCS */
        textMinedRow.setEvidence("PCS");

        // Now see if we have seen this annotation before!
        boolean textMinedItemNotCurrentlyInTable = true;
        for (int idx = 0; idx < table.getItems().size(); idx++) {
            PhenoRow currentTableRow = table.getItems().get(idx);
            if (currentTableRow.getPhenotypeID().equals(textMinedRow.getPhenotypeID())) {
                AnnotationCheckFactory factory = new AnnotationCheckFactory();
                PhenoRow candidateRow = factory.showDialog(currentTableRow, textMinedRow, this.primaryStage);
                if (factory.updateAnnotation()) {
                    table.getItems().set(idx, candidateRow);
                    dirty = true;
                    textMinedItemNotCurrentlyInTable = false;
                }
            }
        }
        if (textMinedItemNotCurrentlyInTable) {// not a duplicate -- just add the new annotation
            table.getItems().add(textMinedRow);
            dirty = true;
        }
    }


    public void addAnnotation() {
        PhenoRow row = new PhenoRow();
        // Disease ID (OMIM)
        String diseaseID = null;
        String diseaseName = this.diseaseNameTextField.getText().trim();
        // default to the disease name in the first row of the table's current entry
        if (diseaseName.length() < 3) {
            if (table.getItems().size() > 0) {
                diseaseName = table.getItems().get(0).getDiseaseName();
                diseaseID = table.getItems().get(0).getDiseaseID();
            }
        } else {
            diseaseID = this.omimName2IdMap.get(diseaseName);
            if (diseaseID == null) {
                diseaseID = "?";
            } else {/* the map mcontains items such as 612342, but we want OMIM:612342 */
                diseaseID = String.format("OMIM:%s", diseaseID);
            }
        }

        row.setDiseaseID(diseaseID);
        row.setDiseaseName(diseaseName);
        // HPO Id
        String hpoId;
        String hpoSynonym = this.hpoNameTextField.getText().trim();
        String hpoPreferredLabel = this.hpoSynonym2LabelMap.get(hpoSynonym);
        hpoId = this.hponame2idMap.get(hpoPreferredLabel);
        row.setPhenotypeID(hpoId);
        row.setPhenotypeName(hpoPreferredLabel);
        String evidence = "?";
        if (IEAbutton.isSelected())
            evidence = "IEA";
        else if (ICEbutton.isSelected())
            evidence = "ICE";
        else if (PCSbutton.isSelected())
            evidence = "PCS";
        else if (TASbutton.isSelected())
            evidence = "TAS";
        row.setEvidence(evidence);
        // Age of onset
        String onsetID, onsetName;
        onsetName = ageOfOnsetChoiceBox.getValue();
        if (onsetName != null) {
            onsetID = hpoOnset.getID(onsetName);
            row.setOnsetID(onsetID);
            row.setOnsetName(onsetName);
        }
        String frequencyName;
        String freq = this.frequencyChoiceBox.getValue();
        if (freq != null) {
            frequencyName = freq;
        } else {
            frequencyName = this.frequencyTextField.getText().trim();
        }
        if (frequencyName.length() > 2) {
            // todo allow to set HPO ids.
            row.setFrequency(frequencyName);
        }
        String negation = null;
        if (this.notBox.isSelected()) {
            row.setNegation("NOT");
        }
        String desc = this.descriptiontextField.getText();
        if (desc != null && desc.length() > 2) {
            row.setDescription(desc);
        }

        boolean useLastSource = false;
        if (this.lastSourceBox.isSelected()) {
            useLastSource = true;
            this.lastSourceBox.setSelected(false);
        }
        String src = this.pubTextField.getText();
        if (src != null && src.length() > 2) {
            row.setPublication(src);
            this.lastSource.setValue(src);
        } else if (useLastSource && this.lastSource.getValue().length() > 0) {
            row.setPublication(this.lastSource.getValue());
        } else if (diseaseID != null) {
            // default to the name of the disease in the Model
            String question = String.format("Should we use the diseaseID \"%s\"?", diseaseID);
            boolean addId = PopUps.getBooleanFromUser(question, "No Citation found", "Need to add citation");
            if (addId) {
                row.setEvidence("TAS");
                row.setPublication(diseaseID);
            }
        }

        String modifier = this.modifiertextField.getText();
        if (modifier != null && this.hpoModifer2idMap.containsKey(modifier)) {
            row.setModifier(hpoModifer2idMap.get(modifier));
        }

        String bcurator = this.settings.getBioCuratorId();
        if (bcurator != null && !bcurator.equals("null")) {
            String biocuration = String.format("%s[%s]", bcurator, getDate());
            row.setBiocuration(biocuration);
        }

        table.getItems().add(row);
        clearFields();
        dirty = true;
    }

    /**
     * Resets all of the fields after the user has entered a new annotation.
     */
    private void clearFields() {
        this.diseaseNameTextField.clear();
        this.hpoNameTextField.clear();
        this.IEAbutton.setSelected(true);
        this.frequencyTextField.clear();
        this.notBox.setSelected(false);
        this.descriptiontextField.clear();
        this.pubTextField.clear();
        this.frequencyChoiceBox.setValue(null);
        this.ageOfOnsetChoiceBox.setValue(null);
        this.modifiertextField.clear();
    }


    private String getDate() {
        Date dNow = new Date();
        SimpleDateFormat ft = new SimpleDateFormat("yyyy-MM-dd");
        return ft.format(dNow);
    }

    /**
     * Delete the marked row of the table.
     */
    @FXML
    private void deleteAnnotation() {
        ObservableList<PhenoRow> phenoSelected, allPheno;
        allPheno = table.getItems();
        phenoSelected = table.getSelectionModel().getSelectedItems();
        //phenoSelected.removeAll();
        phenoSelected.forEach(allPheno::remove);
        dirty = true;
    }

    @FXML
    public void showLog(ActionEvent e) {
        LogViewerFactory factory = new LogViewerFactory();
        factory.display();
        e.consume();
    }

    /**
     * Create PopUp window with text-mining widget allowing to perform the mining. Process results
     */
    @FXML
    public void fetchTextMining() {
        if (ontologizerOntology == null) {
            if (!Platform.checkHPOFileDownloaded()) {
                System.err.println("Unable to perform text mining, download HP OBO file first");
                return;
            }
            try {
                HPOParser p = new HPOParser(settings.getHpoFile());
                ontologizerOntology = p.getOntologizerOntology(settings.getHpoFile());
            } catch (Exception e) {
                PopUps.showException("I/O Error",
                        "Could not input hp.obo file",
                        String.format("Unable to perform text mining, error parsing OBO file from location" +
                                        " %s",
                                settings.getHpoFile()),
                        e);
                return;
            }
        }
        // at this point we either have ontology, or we printed an error.

        Stage stage = (Stage) this.anchorpane.getScene().getWindow();
        String server = "http://phenotyper.monarchinitiative.org:5678/cr/annotate";
        URL url = null;
        try {
            url = new URL(server);
        } catch (MalformedURLException e) {
            System.err.println(String.format("Error parsing url string of text mining server: %s", server));
        }

        HPOTextMining textMiningAnalysis = new HPOTextMining(ontologizerOntology, url, stage);

        TextMiningResult result = textMiningAnalysis.runAnalysis();

        Set<PhenotypeTerm> approvedTerms = result.getTerms();   // set of terms approved by the curator
        String pmid = result.getPmid();              // PMID of the publication

        approvedTerms.forEach(term -> addTextMinedAnnotation(term.getHpoId(), term.getName(), pmid, !term.isPresent()));
        if (approvedTerms.size() > 0) dirty = true;
    }


    /**
     * Show the about message
     */
    public void aboutWindow(ActionEvent e) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("PhenoteFX");
        alert.setHeaderText("PhenoteFX");
        String s = "A tool for revising and creating\nHPO Annotation files for rare disease.";
        alert.setContentText(s);
        alert.showAndWait();
        e.consume();
    }

    /**
     * @param e event triggered by show help command.
     */
    @FXML
    public void showHelpWindow(ActionEvent e) {
        logger.trace("Show help window");
        HelpViewFactory.openHelpDialog();
        e.consume();
    }

    /**
     * Check the contents of the table rows and make sure the format is valid before we start to save the file.
     *
     * @return true if the phenorows are all valid.
     */
    private boolean checkFileValidity() {
        List<PhenoRow> phenorows = table.getItems();
        SmallFileValidator validator = new SmallFileValidator(phenorows);
        if (validator.isValid()) {
            return true;
        } else {
            PopUps.showInfoMessage(validator.errorMessage(), "Please correct error in annotation data");
            return false;
        }
    }


    private void savePhenoteFileAt(File file) {
        if (!checkFileValidity()) return;
        if (file == null) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("HPO Phenote");
            alert.setHeaderText("Error");
            String s = "Could not retrieve name of file to save";
            alert.setContentText(s);
            alert.showAndWait();
            return;
        }
        try {
            BufferedWriter br = new BufferedWriter(new FileWriter(file));
            br.write(header + "\n");
            List<PhenoRow> phenorows = table.getItems();
            for (PhenoRow pr : phenorows) {
                br.write(pr.toString() + "\n");
            }
            br.close();
            dirty = false;
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * Save the modified file at the original location, showing a file chooser so the user can confirm
     */
    public void savePhenoteFile() {
        if (!checkFileValidity()) return;
        if (this.currentPhenoteFileFullPath == null) {
            saveAsPhenoteFile();
            return;
        }
        boolean doWrite = PopUps.getBooleanFromUser("Overwrite original file?",
                String.format("Save to %s", this.currentPhenoteFileFullPath), "Save file?");
        if (doWrite) {
            File f = new File(this.currentPhenoteFileFullPath);
            savePhenoteFileAt(f);
            dirty = false;
        }
    }

    /**
     * Save the modified file at a location chosen by user
     */
    public void saveAsPhenoteFile() {
        FileChooser fileChooser = new FileChooser();
        Stage stage = (Stage) this.anchorpane.getScene().getWindow();
        String defaultdir = settings.getDefaultDirectory();
        //Set extension filter
        FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("TAB/TSV files (*.tab)", "*.tab");
        fileChooser.getExtensionFilters().add(extFilter);
        fileChooser.setInitialFileName(this.currentPhenoteFileBaseName);
        fileChooser.setInitialDirectory(new File(defaultdir));
        //Show save file dialog
        File file = fileChooser.showSaveDialog(stage);
        savePhenoteFileAt(file);
        this.currentPhenoteFileFullPath = file.getAbsolutePath();
        dirty = false;
    }


    /**
     * Runs after user clicks Settings/Set biocurator MenuItem and asks user to provide the ID.
     */
    @FXML
    void setBiocuratorMenuItemClicked(ActionEvent event) {
        String biocurator = PopUps.getStringFromUser("Biocurator ID",
                "e.g. HPO:rrabbit", "Enter your biocurator ID:");
        if (biocurator != null) {
            this.settings.setBioCuratorId(biocurator);
            PopUps.showInfoMessage(String.format("Biocurator ID set to \n\"%s\"",
                    biocurator), "Success");
        } else {
            PopUps.showInfoMessage("Biocurator ID not set.",
                    "Information");
        }
        event.consume();
        saveSettings();
    }


    @FXML
    public void showSettings() {
        SettingsViewFactory.showSettings(this.settings);
    }

    @FXML
    public void newFile() {
        if (dirty) {
            boolean discard = PopUps.getBooleanFromUser("Discard unsaved changes?", "Unsaved work on current annotation file", "Discard unsaved work?");
            if (!discard) return;
        }
        clearFields();
        table.getItems().clear();
        this.currentPhenoteFileFullPath = null;
        this.currentPhenoteFileBaseName = null;
        this.lastSource.setValue("");
        PhenoRow row = new PhenoRow();
        NewItemFactory factory = new NewItemFactory();
        String now = getDate();
        factory.setBiocurator(this.settings.getBioCuratorId(), now);
        boolean ok = factory.showDialog();
        if (ok) row = factory.getProw();
        String diseaseId = row.getDiseaseID();
        if (diseaseId.contains(":")) {
            int i = diseaseId.indexOf(":");
            String prefix = diseaseId.substring(0, i);// part before ":"
            String number = diseaseId.substring(i + 1);// part after ":"
            this.currentPhenoteFileBaseName = String.format("%s-%s.tab", prefix, number);
        }
        dirty = true;
        table.getItems().add(row);
    }

    @FXML
    public void openByMIMnumber() {
        if (dirty) {
            boolean discard = PopUps.getBooleanFromUser("Discard unsaved changes?", "Unsaved work on current annotation file", "Discard unsaved work?");
            if (!discard) return;
        }
        String dirpath = settings.getDefaultDirectory();
        if (dirpath == null) {
            PopUps.showInfoMessage("Please set default Phenote directory\n in Settings menu",
                    "Error: Default directory not set");
            return;
        }
        String mimID = PopUps.getStringFromUser("Enter MIM ID to open",
                "Enter the 6 digit MIM id of the Phenote file to open",
                "MIM id");
        mimID = mimID.trim();
        Integer i = null;
        try {
            i = Integer.parseInt(mimID);
        } catch (NumberFormatException nfe) {
            PopUps.showException("Error getting MIM ID",
                    String.format("Malformed MIM ID entered: %s", mimID),
                    nfe.toString(), nfe);
        }
        if (mimID.length() != 6) {
            PopUps.showInfoMessage(String.format("MIMId needs to be 6 digits (you entered: %s", mimID),
                    "Error: Malformed MIM ID");
            return;
        }

        String basename = String.format("OMIM-%d.tab", i);
        File f = new File(dirpath + File.separator + basename);
        if (!f.exists()) {
            PopUps.showInfoMessage(String.format("Could not find file %s at \n%s", basename, f.getAbsoluteFile()),
                    "Error: Malformed MIM ID");
            return;
        }
        populateTable(f);

    }

    @FXML
    public void setDefaultPhenoteFileDirectory() {
        Stage stage = (Stage) this.anchorpane.getScene().getWindow();
        File dir = PopUps.selectDirectory(stage, null, "Choose default Phenote file directory");
        this.settings.setDefaultDirectory(dir.getAbsolutePath());
        saveSettings();
    }

    @FXML
    private void findPercentage(ActionEvent e) {
        e.consume();
        PercentageFinder pfinder = new PercentageFinder();
    }


}
