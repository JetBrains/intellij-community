package com.intellij.database.view.ui;

import com.intellij.database.DataGridBundle;
import com.intellij.database.csv.CsvFormat;
import com.intellij.database.csv.CsvFormatsSettings;
import com.intellij.database.datagrid.*;
import com.intellij.database.datagrid.DataConsumer.Column;
import com.intellij.database.datagrid.DataConsumer.Row;
import com.intellij.database.extractors.*;
import com.intellij.database.extractors.BaseExtractorsHelper.Script;
import com.intellij.database.run.actions.DumpSource;
import com.intellij.database.run.actions.DumpSource.DataGridSource;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.database.run.ui.text.TextResultView;
import com.intellij.database.util.Out;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.PlainTextLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.*;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.components.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.sql.Types;
import java.util.List;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static com.intellij.database.extractors.DataExtractorFactories.getBuiltInFactories;
import static com.intellij.database.extractors.GridExtractorsUtilCore.prepareFileName;

/**
 * @author Liudmila Kornilova
 **/
public class DumpDataForm {
  private static final List<Column> PREVIEW_COLUMNS = Arrays.asList(
    new Column(0, "id", Types.INTEGER, "int", "java.lang.Integer"),
    new Column(1, "first_name", Types.VARCHAR, "varchar", "java.lang.String"),
    new Column(2, "last_name", Types.VARCHAR, "varchar", "java.lang.String"),
    new Column(3, "birth", Types.VARCHAR, "varchar", "java.lang.String"));
  private static final List<Row> PREVIEW_ROWS = Arrays.asList(
    Row.create(0, new Object[]{10001, "Georgi", "Facello", "M", "1953-09-02"}),
    Row.create(1, new Object[]{10002, "Bezalel", "Simmel", "F", "1964-06-02"}),
    Row.create(2, new Object[]{10003, "Parto", "Bamford", "M", "1959-12-03"}),
    Row.create(3, new Object[]{10004, "Chirstian", "Koblick", "M", "1954-05-01"}),
    Row.create(4, new Object[]{10005, "Kyoichi", "Maliniak", "M", "1955-01-21"}),
    Row.create(5, new Object[]{10006, "Anneke", "Preusig", "F", "1953-04-20"}),
    Row.create(6, new Object[]{10007, "Tzvetan", "Zielinski", "F", "1957-05-23"}),
    Row.create(7, new Object[]{10008, "Saniya", "Kalloufi", "M", "1958-02-19"}),
    Row.create(8, new Object[]{10009, "Sumant", "Peac", "F", "1952-04-19"}),
    Row.create(9, new Object[]{10010, "Duangkaew", "Piveteau", "F", "1963-06-01"}));

  private static final int MAX_ROWS_FOR_PREVIEW = 10;
  private static final int MAX_SOURCE_HEIGHT = 100;
  private static final Logger LOG = Logger.getInstance(DumpDataForm.class);
  private static final Set<String> EXTRACTORS_NO_TRANSPOSE = Set.of("SQL-Insert-Statements.sql.groovy",
                                                                               "JSON-Groovy.json.groovy");
  private final Project myProject;
  private final DumpSource<?> mySource;
  private final Supplier<Window> myWindowSupplier;
  private final boolean mySupportsAddQuery;
  private final EditorEx myViewer;
  private final JBPanelWithEmptyText emptyTextPanel =
    new JBPanelWithEmptyText().withEmptyText(DataGridBundle.message("settings.database.DumpDialog.Preview.NoPreview"));

  public JPanel myPanel;
  private JBCheckBox myAddComputed;
  private JBCheckBox myAddGenerated;
  private JBCheckBox myAddTableDefinition;
  private LabeledComponent<JComponent> myPreviewLabeledComponent;
  private JBCheckBox myTranspose;
  private JBLabel myAddColumnsLabel;
  private LabeledComponentNoThrow<TextFieldWithBrowseButton> myOutputFileOrDirectory;
  private final OutputPathManager myOutputPathManager;
  private LabeledComponentNoThrow<ComboBox<DataExtractorFactory>> myExtractorCombobox;
  private LabeledComponentNoThrow<JBScrollPane> mySourceLabeledComponent;
  private JBCheckBox myAddColumnHeader;
  private JBCheckBox myAddRowHeader;
  private JBCheckBox myAddQuery;

  protected Disposable myDisposable;

  public DumpDataForm(@NotNull Project project,
                      @NotNull DumpSource<?> source,
                      @NotNull Supplier<Window> windowSupplier,
                      @Nullable CsvFormatsSettings csvFormatsSettings,
                      @NotNull Disposable disposable,
                      boolean supportsAddQuery) {
    myProject = project;
    mySource = source;
    myWindowSupplier = windowSupplier;
    mySupportsAddQuery = supportsAddQuery;
    String sourceText = getSourceText(mySource);
    if (sourceText == null) {
      mySourceLabeledComponent.setVisible(false);
    }
    else {
      JEditorPane pane = new JEditorPane();
      pane.setMargin(JBUI.insets(5));
      pane.setEditable(false);
      pane.setText(sourceText);
      pane.setBackground(UIUtil.getPanelBackground());

      JBScrollPane scrollPane = mySourceLabeledComponent.getComponent();
      scrollPane.setViewportView(pane);
      if (scrollPane.getPreferredSize().height > MAX_SOURCE_HEIGHT) {
        scrollPane.setPreferredSize(new Dimension(-1, 100));
      }
    }
    List<DataExtractorFactory> factories = new ArrayList<>();
    CoreGrid<GridRow, GridColumn> grid = source instanceof DataGridSource ? ((DataGridSource)source).getGrid() : null;
    factories.addAll(getBuiltInFactories(grid));
    factories.addAll(DataExtractorFactories.getCsvFormats(csvFormatsSettings));
    List<DataExtractorFactory> allScripts =
      DataExtractorFactories.getExtractorScripts(ExtractorsHelper.getInstance(grid), GridUtil::suggestPlugin);
    List<DataExtractorFactory> scripts = ContainerUtil.sorted(allScripts, Comparator.comparing(s -> StringUtil.toLowerCase(s.getName())));
    factories.addAll(scripts);
    String currentFactoryId =
      DataExtractorProperties.getCurrentExportExtractorFactory(myProject, GridUtil::suggestPlugin, csvFormatsSettings).getId();
    ComboBox<DataExtractorFactory> comboBox = myExtractorCombobox.getComponent();
    comboBox.setSwingPopup(false);
    comboBox.setModel(new DefaultComboBoxModel<>(factories.toArray(new DataExtractorFactory[0])));
    comboBox.setRenderer(SimpleListCellRenderer.create("", f -> DataExtractorFactories.getDisplayName(f, scripts)));
    DataExtractorFactory currentFactory = ContainerUtil.find(factories, factory -> currentFactoryId.equals(factory.getId()));
    comboBox.setSelectedItem(currentFactory);
    comboBox.addItemListener(e -> {
      if (e.getStateChange() == ItemEvent.SELECTED) {
        updateCheckboxes();
        updateFileExtension();
      }
    });
    updateCheckboxes();

    myAddComputed.setSelected(!DataExtractorProperties.isSkipComputed());
    myAddComputed.addItemListener(e -> settingsChanged());
    myAddGenerated.setSelected(!DataExtractorProperties.isSkipGeneratedColumns());
    myAddGenerated.addItemListener(e -> settingsChanged());
    myAddTableDefinition.setSelected(DataExtractorProperties.isIncludeCreateTable());
    myAddTableDefinition.addItemListener(e -> settingsChanged());
    myTranspose.setSelected(mySource instanceof DataGridSource && ((DataGridSource)mySource).getGrid().getResultView().isTransposed());
    myTranspose.addItemListener(e -> settingsChanged());
    myAddColumnHeader.addItemListener(e -> settingsChanged());
    myAddRowHeader.addItemListener(e -> settingsChanged());
    myAddQuery.setSelected(DataExtractorProperties.isIncludeQuery());
    myAddQuery.addItemListener(e -> settingsChanged());

    myOutputPathManager = DumpSource.getSize(source) == 1
                          ? new OutputFilePathManager()
                          : new OutputDirectoryPathManager();
    myOutputFileOrDirectory.setText(myOutputPathManager.getFieldName());
    final String defaultFileName = prepareFileName(getName(mySource));
    myOutputFileOrDirectory.getComponent().setText(getDefaultPath(defaultFileName, currentFactory));
    myOutputFileOrDirectory.getComponent().getTextField().addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        myOutputPathManager.focusGained(myOutputFileOrDirectory.getComponent());
      }

      @Override
      public void focusLost(FocusEvent e) { }
    });
    FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileOrFolderDescriptor();
    descriptor.setTitle(DataGridBundle.message("settings.database.DumpDialog.FileChooser.Title"));
    myOutputFileOrDirectory.getComponent().addBrowseFolderListener(new TextBrowseFolderListener(descriptor, project) {
      @Override
      protected @NotNull String chosenFileToResultingText(@NotNull VirtualFile chosenFile) {
        DataExtractorFactory factory = getFactory();
        if (factory == null) return super.chosenFileToResultingText(chosenFile);
        return myOutputPathManager.adjustChosenFile(chosenFile.toNioPath(), defaultFileName, factory.getFileExtension()).toString();
      }
    });

    myViewer = TextResultView.createEditor(myProject, "dumpDataPreview");
    Disposer.register(disposable, () -> EditorFactory.getInstance().releaseEditor(myViewer));
    myDisposable = Disposer.newDisposable();
    Disposer.register(disposable, myDisposable);

    var myPane = new JBLayeredPane();
    myPane.add(myViewer.getComponent(), JLayeredPane.DEFAULT_LAYER);
    myPane.setFullOverlayLayout(true);
    myPane.add(emptyTextPanel, JLayeredPane.PALETTE_LAYER);
    myPreviewLabeledComponent.setComponent(myPane);
    emptyTextPanel.setVisible(false);

    comboBox.addItemListener(e -> {
      if (e.getStateChange() == ItemEvent.SELECTED) {
        settingsChanged();
      }
    });
  }

  private @NotNull String getDefaultPath(String defaultFileName, DataExtractorFactory currentFactory) {
    try {
      Path path = Path.of(DataExtractorProperties.getOutputDir());
      Path filePath = myOutputPathManager.getFilePath(path, defaultFileName, currentFactory.getFileExtension());
      return filePath.toString();
    }
    catch (InvalidPathException e) {
      LOG.warn(e);
      return "";
    }
  }

  private static @NotNull <T> String getName(DumpSource<T> source) {
    JBIterable<T> sources = source.getSources();
    T first = sources.first();
    String name = first == null ? null : source.getNameProvider().getName(first);
    return name == null ? "" : name;
  }

  public @NotNull DumpDataForm.OutputPathManager getOutputPathManager() {
    return myOutputPathManager;
  }

  protected @NlsSafe @Nullable String getSourceText(@Nullable DumpSource<?> source) {
    return null;
  }

  public @NotNull JTextField getOutputFileOrDirectoryField() {
    return myOutputFileOrDirectory.getComponent().getTextField();
  }

  public @NotNull ComboBox<DataExtractorFactory> getExtractorComboBox() {
    return myExtractorCombobox.getComponent();
  }

  private void updateFileExtension() {
    DataExtractorFactory factory = getFactory();
    if (factory != null) {
      try {
        Path newPath = myOutputPathManager.updateFileExtension(getFilePath(), factory.getFileExtension());
        myOutputFileOrDirectory.getComponent().setText(newPath.toString());
      }
      catch (InvalidPathException e) {
        LOG.warn(e);
      }
    }
  }

  private @NotNull Path getFilePath() {
    return Path.of(myOutputFileOrDirectory.getComponent().getText());
  }

  private void updateCheckboxes() {
    DataExtractorFactory factory = getFactory();
    if (factory == null) return;
    myTranspose.setVisible(supportsTranspose(factory));
    myAddTableDefinition.setVisible(supportsAddTableDefinition(factory));
    boolean supportsComputedOrGenerated = supportsAddComputedOrGeneratedColumns(factory);
    myAddColumnsLabel.setVisible(supportsComputedOrGenerated);
    myAddComputed.setVisible(supportsComputedOrGenerated);
    myAddGenerated.setVisible(supportsComputedOrGenerated);
    myAddColumnHeader.setVisible(factory instanceof FormatExtractorFactory);
    myAddRowHeader.setVisible(factory instanceof FormatExtractorFactory);
    myAddQuery.setVisible(mySupportsAddQuery && factory instanceof XlsxExtractorFactory);
    if (factory instanceof FormatExtractorFactory) {
      CsvFormat format = ((FormatExtractorFactory)factory).getFormat();
      myAddColumnHeader.setSelected(format.headerRecord != null);
      myAddRowHeader.setSelected(format.rowNumbers);
    }
  }

  public @Nullable DataExtractorFactory getFactory() {
    return ObjectUtils.tryCast(myExtractorCombobox.getComponent().getSelectedItem(), DataExtractorFactory.class);
  }

  protected boolean supportsAddComputedOrGeneratedColumns(DataExtractorFactory factory) {
    return false;
  }

  protected boolean supportsTranspose(DataExtractorFactory factory) {
    return !(factory instanceof Script && EXTRACTORS_NO_TRANSPOSE.contains(factory.getName()));
  }

  protected boolean supportsAddTableDefinition(DataExtractorFactory factory) {
    return false;
  }

  private void settingsChanged() {
    saveState();
    ApplicationManager.getApplication().invokeLater(() -> this.updatePreview(), ModalityState.stateForComponent(myWindowSupplier.get()));
  }

  public void updatePreview() {
    List<? extends GridRow> rows;
    List<? extends GridColumn> columns;
    DataGrid grid = mySource instanceof DataGridSource ? ((DataGridSource)mySource).getGrid() : null;
    if (grid != null) {
      GridModel<GridRow, GridColumn> model = grid.getDataModel(DataAccessType.DATABASE_DATA);
      rows = model.getRows(ModelIndexSet.forRows(grid, IntStream.range(0, Math.min(MAX_ROWS_FOR_PREVIEW, model.getRowCount())).toArray()));
      columns = model.getAllColumnsForExtraction();
    }
    else {
      rows = getRows(mySource);
      columns = getColumns(mySource);
    }
    DataExtractorFactory extractorFactory = getFactory();
    DataExtractor extractor = extractorFactory == null ? null : extractorFactory.createExtractor(createConfig(myProject, mySource));
    if (extractor == null) {
      LOG.error(
        "Cannot create data extractor. DataExtractorFactory: " + (extractorFactory == null ? "unknown" : extractorFactory.getName()));
      TextResultView.updateEditorText(myViewer, myProject, "", PlainTextLanguage.INSTANCE);
      emptyTextPanel.setVisible(false);
      return;
    }
    if (!extractor.supportsText()) {
      emptyTextPanel.setVisible(true);
      TextResultView.updateEditorText(myViewer, myProject, "Extractor is binary. Preview is not available", PlainTextLanguage.INSTANCE);
      return;
    }
    var out = new Out.Readable();
    GridExtractorsUtilCore.extract(out,
                                   getExtractorConfig(),
                                   columns,
                                   extractor,
                                   rows,
                                   grid != null ? grid.getVisibleColumns().asArray() : new int[]{});
    emptyTextPanel.setVisible(false);
    TextResultView.updateEditorText(myViewer, myProject,
                                    out.getString(),
                                    guessLanguage()
    );
  }

  protected List<? extends GridColumn> getColumns(@NotNull DumpSource<?> source) {
    return PREVIEW_COLUMNS;
  }

  protected List<? extends GridRow> getRows(@NotNull DumpSource<?> source) {
    return PREVIEW_ROWS;
  }

  protected @NotNull ExtractorConfig createConfig(@NotNull Project project, @NotNull DumpSource<?> source) {
    DataGrid grid = mySource instanceof DataGridSource ? ((DataGridSource)mySource).getGrid() : null;
    return new BaseExtractorConfig(grid != null ? grid.getObjectFormatter() : new BaseObjectFormatter(), project);
  }

  private @NotNull Language guessLanguage() {
    DataExtractorFactory selectedFactory = getFactory();
    return guessLanguage(selectedFactory == null ? null : selectedFactory.getFileExtension());
  }

  private static @NotNull Language guessLanguage(@Nullable String extension) {
    if (extension == null) return PlainTextLanguage.INSTANCE;
    return ObjectUtils.notNull(LanguageUtil.getFileTypeLanguage(FileTypeRegistry.getInstance().getFileTypeByExtension(extension)),
                               PlainTextLanguage.INSTANCE);
  }

  public ExtractionConfig getExtractorConfig() {
    return new ExtractionConfig(get(myAddTableDefinition), get(myTranspose), get(myAddComputed),
                                get(myAddGenerated), get(myAddColumnHeader), get(myAddRowHeader),
                                get(myAddQuery), false);
  }

  private static boolean get(@NotNull JBCheckBox checkBox) {
    return checkBox.isVisible() && checkBox.isEnabled() && checkBox.isSelected();
  }

  public void saveState() {
    if (enabled(myAddComputed)) DataExtractorProperties.setSkipComputed(!myAddComputed.isSelected());
    if (enabled(myAddGenerated)) DataExtractorProperties.setSkipGeneratedColumns(!myAddGenerated.isSelected());
    if (enabled(myAddTableDefinition)) DataExtractorProperties.setIncludeCreateTable(myAddTableDefinition.isSelected());
    if (enabled(myAddQuery)) DataExtractorProperties.setIncludeQuery(myAddQuery.isSelected());

    String filePath = myOutputFileOrDirectory.getComponent().getText();
    DataExtractorProperties.setOutputDir(getDir(filePath));

    DataExtractorFactory factory = getFactory();
    if (factory != null) DataExtractorProperties.setCurrentExportExtractorFactory(myProject, factory);
  }

  private static boolean enabled(JBCheckBox checkbox) {
    return checkbox.isVisible() && checkbox.isEnabled();
  }

  private static String getDir(String filePath) {
    int lastSlash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
    return lastSlash == -1
           ? StringUtil.containsChar(filePath, '.') ? "" : filePath
           : filePath.length() - 1 == lastSlash
             ? filePath.substring(0, filePath.length() - 1)
             : filePath.indexOf('.', lastSlash) == -1 ? filePath : filePath.substring(0, lastSlash);
  }

  public interface OutputPathManager {
    @NotNull Path getFilePath(@NotNull Path dirPath, @NotNull String defaultName, @NotNull String extension);
    @NlsContexts.Label
    @NotNull String getFieldName();
    @NotNull Path updateFileExtension(@NotNull Path filePath, @NotNull String extension);
    @NotNull Path adjustChosenFile(@NotNull Path path, @NotNull String defaultName, @NotNull String extension);
    @NlsContexts.DialogMessage
    @Nullable String validatePath(String path);
    void focusGained(@NotNull TextFieldWithBrowseButton field);
  }

  private static class OutputFilePathManager implements OutputPathManager {
    @Override
    public @NotNull Path getFilePath(@NotNull Path dirPath,
                                     @NotNull String defaultName,
                                     @NotNull String extension) {
      return dirPath.resolve(defaultName + "." + extension);
    }

    @Override
    public @NotNull String getFieldName() {
      return DataGridBundle.message("settings.database.DumpDialog.SaveTo.File");
    }

    @Override
    public void focusGained(@NotNull TextFieldWithBrowseButton field) {
      String filePath = field.getText();
      int start = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\')) + 1;
      if (start >= filePath.length()) return;
      int end = StringUtilRt.lastIndexOf(filePath, '.', start, filePath.length());
      if (end != -1) field.getTextField().select(start, end);
    }

    @Override
    public @NotNull Path updateFileExtension(@NotNull Path filePath, @NotNull String extension) {
      //if (StringUtil.isEmptyOrSpaces(filePath)) return filePath;
      if (Files.isDirectory(filePath) ||
          FileUtilRt.extensionEquals(filePath.getFileName().toString(), extension)) {
        return filePath;
      }
      String name = FileUtilRt.getNameWithoutExtension(filePath.getFileName().toString());
      return filePath.resolveSibling(name + "." + extension);
    }

    @Override
    public @NotNull Path adjustChosenFile(@NotNull Path path,
                                          @NotNull String defaultName,
                                          @NotNull String extension) {
      return Files.isDirectory(path)
             ? getFilePath(path, defaultName, extension)
             : path;
    }

    @Override
    public String validatePath(String path) {
      VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path);
      if (virtualFile != null) {
        return virtualFile.isDirectory()
               ? DataGridBundle.message("settings.database.DumpDialog.Errors.NotFile")
               : null;
      }
      VirtualFile parentFile = LocalFileSystem.getInstance().findFileByPath(PathUtil.getParentPath(path));
      return parentFile == null || !parentFile.isDirectory()
             ? DataGridBundle.message("settings.database.DumpDialog.Errors.ParentDirNotExist")
             : null;
    }
  }

  private static class OutputDirectoryPathManager implements OutputPathManager {
    @Override
    public @NotNull Path getFilePath(@NotNull Path dirPath,
                                     @NotNull String defaultName,
                                     @NotNull String extension) {
      return dirPath;
    }

    @Override
    public @NotNull String getFieldName() {
      return DataGridBundle.message("settings.database.DumpDialog.SaveTo.Directory");
    }

    @Override
    public @NotNull Path updateFileExtension(@NotNull Path filePath, @NotNull String extension) {
      return filePath;
    }

    @Override
    public @NotNull Path adjustChosenFile(@NotNull Path path, @NotNull String defaultName, @NotNull String extension) {
      return Files.isDirectory(path)
             ? path
             : path.getParent();
    }

    @Override
    public String validatePath(String path) {
      VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path);
      return virtualFile == null
             ? DataGridBundle.message("settings.database.DumpDialog.Errors.DirNotExist")
             : !virtualFile.isDirectory()
               ? DataGridBundle.message("settings.database.DumpDialog.Errors.NotDir")
               : null;
    }

    @Override
    public void focusGained(@NotNull TextFieldWithBrowseButton field) {
    }
  }
}
