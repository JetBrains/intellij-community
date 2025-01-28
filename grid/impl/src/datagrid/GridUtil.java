package com.intellij.database.datagrid;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.database.DataGridBundle;
import com.intellij.database.DatabaseDataKeys;
import com.intellij.database.connection.throwable.info.ErrorInfo;
import com.intellij.database.csv.CsvFormat;
import com.intellij.database.csv.CsvSettingsService;
import com.intellij.database.data.types.BaseConversionGraph;
import com.intellij.database.data.types.DataTypeConversion;
import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel.HierarchicalGridColumn;
import com.intellij.database.editor.TableEditorBase;
import com.intellij.database.extractors.*;
import com.intellij.database.extractors.DatabaseObjectFormatterConfig.DatabaseDisplayObjectFormatterConfig;
import com.intellij.database.remote.jdbc.LobInfo;
import com.intellij.database.run.actions.ChoosePasteFormatAction;
import com.intellij.database.run.ui.*;
import com.intellij.database.run.ui.grid.CellAttributesKey;
import com.intellij.database.run.ui.grid.GridPasteProvider.TableDataParseResult;
import com.intellij.database.run.ui.grid.GridScrollPositionManager;
import com.intellij.database.run.ui.grid.GridTransferableData;
import com.intellij.database.run.ui.grid.editors.*;
import com.intellij.database.run.ui.grid.renderers.*;
import com.intellij.database.run.ui.treetable.TreeTableResultView;
import com.intellij.database.settings.DataGridAppearanceSettings;
import com.intellij.database.settings.DataGridSettings;
import com.intellij.database.util.Out;
import com.intellij.database.vfs.fragment.TableDataFragmentFile;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.actions.RevealFileAction;
import com.intellij.ide.plugins.PluginNode;
import com.intellij.ide.plugins.marketplace.MarketplaceRequests;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.impl.EditorImpl;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.BalloonBuilder;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.updateSettings.impl.PluginDownloader;
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.PluginsAdvertiserDialog;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.encoding.EncodingManager;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SingleRootFileViewProvider;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.TextFieldWithAutoCompletion;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.components.TwoSideComponent;
import com.intellij.ui.icons.CachedImageIcon;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.textCompletion.TextCompletionProvider;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.TextTransferable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.sql.Types;
import java.util.*;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.intellij.database.DatabaseDataKeys.DATA_GRID_SETTINGS_KEY;
import static com.intellij.database.DatabaseDataKeys.GRID_KEY;
import static com.intellij.database.run.actions.ShowPaginationActionKt.getSHOW_PAGINATION;
import static com.intellij.database.run.ui.DataAccessType.DATA_WITH_MUTATIONS;

public class GridUtil extends GridUtilCore {
  public static final int ADDITIONAL_ROWS_COUNT = 5;
  public static final Key<Boolean> IN_EDITOR_RESULTS = Key.create("IN_EDITOR_RESULTS");
  public static final Key<Boolean> IS_REFERENCED = Key.create("IS_REFERENCED");
  public static final Key<Object> IN_REFERENCE = Key.create("IN_REFERENCE");
  public static final Key<Set<Object>> OUT_REFERENCES = Key.create("OUT_REFERENCES");
  private static final Logger LOG = Logger.getInstance(GridUtil.class);
  private static final Key<TableEditorBase> FILE_EDITOR_KEY = Key.create("ResultPanel.MyTableFileEditor");

  public static final String NULL_TEXT = "null";

  public static boolean hideEditActions(@NotNull DataGrid grid, @Nullable String place) {
    boolean inEditorResults = IN_EDITOR_RESULTS.get(grid, false);
    return "EditorToolbar".equals(place) && inEditorResults && !grid.getDataSupport().hasPendingChanges();
  }

  public static boolean hidePageActions(@NotNull DataGrid grid, @Nullable String place) {
    boolean inEditorResults = IN_EDITOR_RESULTS.get(grid, false);
    return "EditorToolbar".equals(place) && inEditorResults && !getSHOW_PAGINATION().get(grid, false);
  }

  public static void addBottomHeader(@NotNull DataGrid grid) {
    ActionManager actionManager = ActionManager.getInstance();
    ActionGroup actions = (ActionGroup)actionManager.getAction("Console.InEditorTableResult.Horizontal.Group");
    ActionToolbar toolbar = actionManager.createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, actions, true);
    toolbar.setTargetComponent(grid.getPanel().getComponent());
    toolbar.getComponent().setOpaque(false);

    grid.getPanel().setBottomHeaderComponent(toolbar.getComponent());
  }

  public static @NotNull ModelIndex<GridColumn> findColumn(@NotNull DataGrid dataGrid, @Nullable String name) {
    return findColumn(dataGrid.getDataModel(DATA_WITH_MUTATIONS), name, true);
  }

  public static void focusDataGrid(DataGrid grid) {
    if (grid != null) {
      JComponent toFocus = grid.getPreferredFocusedComponent();
      IdeFocusManager.findInstanceByComponent(toFocus).requestFocus(toFocus, true);
    }
  }

  public static @NotNull DataGrid createPreviewDataGrid(@NotNull Project project,
                                                        @NotNull GridDataHookUp<GridRow, GridColumn> dataHookUp,
                                                        @NotNull BiConsumer<DataGrid, DataGridAppearance> configure) {
    ActionGroup popup = (ActionGroup)ActionManager.getInstance().getAction("Console.TableResult.Csv.PreviewPopupGroup");
    ActionGroup columnHeaderActions = (ActionGroup)ActionManager.getInstance().getAction("Console.TableResult.Csv.PreviewColumnHeaderPopup");
    return new TableResultPanel(project, dataHookUp, popup, null, columnHeaderActions, ActionGroup.EMPTY_GROUP, false,
                                configure
                                  .andThen(GridUtil::configureFullSizeTable)
                                  .andThen(GridUtil::disableLocalFilterByDefault)
    );
  }

  public static @NotNull DataGrid createCsvPreviewDataGrid(@NotNull Project project, @NotNull DocumentDataHookUp dataHookUp) {
    return createPreviewDataGrid(project, dataHookUp, GridUtil::configureCsvTable);
  }

  public static @NotNull BiConsumer<DataGrid, DataGridAppearance> configureCsvTable() {
    return GridUtil::configureCsvTable;
  }

  public static void configureCsvTable(@NotNull DataGrid grid, @NotNull DataGridAppearance appearance) {
    putSettings(grid, ( DataGridSettings)CsvSettingsService.getDatabaseSettings());
    GridCellEditorHelper.set(grid, new GridCellEditorHelperImpl());
    GridHelper.set(grid, new GridHelperImpl());
    GridCellEditorFactoryProvider.set(grid, GridCellEditorFactoryImpl.getInstance());
    List<GridCellRendererFactory> factories = Arrays.asList(new DefaultBooleanRendererFactory(grid), new DefaultNumericRendererFactory(grid), new DefaultTextRendererFactory(grid));
    GridCellRendererFactories.set(grid, new GridCellRendererFactories(factories));
    BaseObjectFormatter formatter = new BaseObjectFormatter();
    grid.setObjectFormatterProvider(dataGrid -> formatter);
    BaseConversionGraph.set(grid, new BaseConversionGraph(new FormatsCache(), FormatterCreator.get(grid), () -> grid.getObjectFormatter()));
    appearance.setResultViewShowRowNumbers(true);
    appearance.setBooleanMode(DataGridAppearanceSettings.getSettings().getBooleanMode());
  }

  public static void configureNumericEditor(@NotNull DataGrid grid, @NotNull Editor editor) {
    if (!(editor instanceof EditorImpl editorImpl)) return;
    boolean regular = !grid.getResultView().isTransposed() && grid.getPresentationMode() == GridPresentationMode.TABLE;
    int textAlignment = regular ? EditorImpl.TEXT_ALIGNMENT_RIGHT : EditorImpl.TEXT_ALIGNMENT_LEFT;
    int scrollbarOrientation = regular ? EditorEx.VERTICAL_SCROLLBAR_LEFT : EditorEx.VERTICAL_SCROLLBAR_RIGHT;
    editorImpl.setHorizontalTextAlignment(textAlignment);
    editorImpl.setVerticalScrollbarOrientation(scrollbarOrientation);
  }

  public static void disableLocalFilterByDefault(@NotNull DataGrid grid, @NotNull DataGridAppearance appearance) {
    grid.getLocalFilterState().setEnabled(false);
  }

  public static boolean isFailedToLoad(Object value) {
    return value instanceof String && StringUtil.startsWith((String)value, FAILED_TO_LOAD_PREFIX);
  }

  public static boolean showIgnoreUnsubmittedChangesYesNoDialog(@NotNull DataGrid grid) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return true;

    String title = DataGridBundle.message("dialog.title.ignore.unsubmitted.changes");
    String message = DataGridBundle.message("dialog.message.changes.are.submitted.data.will.be.lost.continue");
    return Messages.YES == Messages.showYesNoDialog(grid.getPanel().getComponent(), message, title, AllIcons.General.NotificationWarning);
  }

  public static void showCannotApplyCellEditorChanges(@NotNull DataGrid grid) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    String title = DataGridBundle.message("dialog.title.cannot.apply.changes");
    String message = DataGridBundle.message("dialog.message.this.table.read.only.changes.cannot.be.applied");
    Messages.showInfoMessage(grid.getPanel().getComponent(), message, title);
  }

  public static @NotNull List<GridRow> getSelectedGridRows(@NotNull DataGrid dataGrid) {
    ModelIndexSet<GridRow> rowIndices = dataGrid.getSelectionModel().getSelectedRows();
    GridModel<GridRow, GridColumn> model = dataGrid.getDataModel(DATA_WITH_MUTATIONS);
    List<GridRow> rows = model.getRows(rowIndices);
    return rows.isEmpty() ? model.getRows() : rows;
  }

  public static int min(@NotNull IndexSet<?> set) {
    OptionalInt min = set.asList()
      .stream()
      .mapToInt(Index::asInteger)
      .min();
    return min.isPresent() ? min.getAsInt() : -1;
  }

  public static @Nullable GridMutator.RowsMutator<GridRow, GridColumn> getRowsMutator(@Nullable DataGrid grid) {
    //noinspection unchecked
    return ObjectUtils.tryCast(grid == null ? null : grid.getDataHookup().getMutator(), GridMutator.RowsMutator.class);
  }

  public static @Nullable GridMutator.ColumnsMutator<GridRow, GridColumn> getColumnsMutator(@Nullable DataGrid grid) {
    //noinspection unchecked
    return ObjectUtils.tryCast(grid == null ? null : grid.getDataHookup().getMutator(), GridMutator.ColumnsMutator.class);
  }

  public static @Nullable GridMutator.DatabaseMutator<GridRow, GridColumn> getDatabaseMutator(@Nullable DataGrid grid) {
    //noinspection unchecked
    return ObjectUtils.tryCast(grid == null ? null : grid.getDataHookup().getMutator(), GridMutator.DatabaseMutator.class);
  }

  public static boolean isInsertedRow(@NotNull DataGrid grid, @NotNull ModelIndex<GridRow> index) {
    GridMutator.RowsMutator<GridRow, GridColumn> mutator = getRowsMutator(grid);
    return mutator != null && mutator.isInsertedRow(index);
  }

  protected static @Nullable ModelIndex<GridRow> getLastNotInsertedRow(@NotNull DataGrid grid) {
    List<ModelIndex<GridRow>> rows = grid.getDataModel(DATA_WITH_MUTATIONS).getRowIndices().asList();
    for (int i = rows.size() - 1; i >= 0; i--) {
      ModelIndex<GridRow> index = rows.get(i);
      if (!isInsertedRow(grid, index)) return index;
    }
    return null;
  }

  protected static int getInsertedRowIdx(@NotNull DataGrid grid, int relativeIndex) {
    ModelIndex<GridRow> rowIdx = getLastNotInsertedRow(grid);
    GridRow row = rowIdx == null ? null : grid.getDataModel(DATA_WITH_MUTATIONS).getRow(rowIdx);
    int humanReadable = relativeIndex + 1;
    if (row == null) return humanReadable;

    int difference = relativeIndex - rowIdx.asInteger();
    return difference < 0 ? humanReadable : row.getRowNum() + difference;
  }

  public static void saveAndRestoreSelection(@NotNull DataGrid grid, Runnable runnable) {
    final GridSelection<GridRow, GridColumn> selection = grid.getSelectionModel().store();
    final GridScrollPositionManager.GridScrollPosition scrollPosition = GridScrollPositionManager.get(grid.getResultView(), grid).store();

    runnable.run();

    // we defer selection and scroll position restoration as logic invoked in setMode may schedule scroll updates.
    ApplicationManager.getApplication().invokeLater(() -> {
      grid.getSelectionModel().restore(selection);
      GridScrollPositionManager.get(grid.getResultView(), grid).restore(scrollPosition);
    });
  }

  public static boolean areOnlySortedColumns(@NotNull List<ModelIndex<GridColumn>> columns, @NotNull DataGrid grid) {
    Set<Integer> newColumns = ContainerUtil.map2Set(columns, c -> c.asInteger());
    return grid.getDataModel(DATA_WITH_MUTATIONS).getColumnIndices().asIterable().find(
      column -> !newColumns.contains(column.asInteger()) &&
                grid.getSortOrder(column) != RowSortOrder.Type.UNSORTED) == null;
  }

  public static void scrollToLocally(@NotNull DataGrid grid, @NotNull ViewIndex<GridRow> row, @NotNull ViewIndex<GridColumn> column) {
    Pair<Integer, Integer> rowAndColumn = grid.getRawIndexConverter().rowAndColumn2Model().fun(row.asInteger(), column.asInteger());
    grid.getSelectionModel().setSelection(ModelIndex.forRow(grid, rowAndColumn.first), ModelIndex.forColumn(grid, rowAndColumn.second));
  }

  public static void scrollToLocally(@NotNull DataGrid grid, @NotNull ViewIndex<GridRow> row) {
    grid.getSelectionModel().setRowSelection(row.toModel(grid), true);
  }

  public static @NotNull String getIconPath(@NotNull Icon icon) {
    if (icon instanceof CachedImageIcon cachedIcon) {
      if (ExperimentalUI.isNewUI()) {
        String path = cachedIcon.getExpUIPath();
        if (path != null) {
          return path;
        }
      }
      String path = cachedIcon.getOriginalPath();
      if (path != null) return path;
    }
    LOG.warn("Don't know how to extract path for " + icon);
    return "actions/stub.svg";
  }

  /**
   * @deprecated Use <code>com.intellij.database.datagrid.DataGrid#getFormatterConfig(com.intellij.database.datagrid.ModelIndex)</code>
   */
  @Deprecated(forRemoval = true)
  public static @NotNull DatabaseDisplayObjectFormatterConfig createFormatterConfig(@NotNull DataGrid grid, @NotNull ModelIndex<GridColumn> column) {
    return Objects.requireNonNull(grid.getFormatterConfig(column));
  }

  public static @NotNull Set<BinaryDisplayType> getAllowedTypes(@NotNull DataGrid dataGrid) {
    Set<BinaryDisplayType> result = EnumSet.allOf(BinaryDisplayType.class);
    if (!isDetectTextInBinaryColumns(dataGrid)) {
      result.remove(BinaryDisplayType.TEXT);
    }
    if (!isDetectUUIDInBinaryColumns(dataGrid)) {
      result.remove(BinaryDisplayType.UUID);
      result.remove(BinaryDisplayType.UUID_SWAP);
    }
    return result;
  }

  public static boolean isDetectTextInBinaryColumns(@NotNull DataGrid grid) {
    Boolean detect = grid.getUserData(DatabaseDataKeys.DETECT_TEXT_IN_BINARY_COLUMNS);
    if (detect != null) return detect;
    detect = getSetting(grid, true, DataGridSettings::isDetectTextInBinaryColumns);
    grid.putUserData(DatabaseDataKeys.DETECT_TEXT_IN_BINARY_COLUMNS, detect);
    return detect;
  }

  public static boolean isDetectUUIDInBinaryColumns(@NotNull DataGrid grid) {
    Boolean detect = grid.getUserData(DatabaseDataKeys.DETECT_UUID_IN_BINARY_COLUMNS);
    if (detect != null) return detect;
    detect = getSetting(grid, true, DataGridSettings::isDetectUUIDInBinaryColumns);
    grid.putUserData(DatabaseDataKeys.DETECT_UUID_IN_BINARY_COLUMNS, detect);
    return detect;
  }

  private static <T> T getSetting(@NotNull DataGrid grid, T defaultValue, @NotNull Function<DataGridSettings, T> function) {
    DataGridSettings settings = getSettings(grid);
    return settings == null ? defaultValue : function.apply(settings);
  }

  public static void globalSchemeChange(@NotNull DataGrid grid, @Nullable EditorColorsScheme scheme) {
    if (scheme == null) return;
    grid.getColorsScheme().setDelegate(scheme);
    grid.getEditorColorsScheme().setDelegate(scheme);
  }

  public static void putSettings(@NotNull DataGrid grid, @Nullable DataGridSettings settings) {
    if (settings == null) return;
    if (grid.getUserData(DATA_GRID_SETTINGS_KEY) != null) {
      LOG.error("Settings are already put. You are overriding them");
    }
    grid.putUserData(DATA_GRID_SETTINGS_KEY, settings);
  }

  public static @Nullable DataGridSettings getSettings(@NotNull DataGrid grid) {
    var result = grid.getUserData(DATA_GRID_SETTINGS_KEY);
    if (result == null) {
      LOG.warn(String.format(
        "No settings for grid %s." +
        "Make sure DATA_GRID_SETTINGS_KEY set for your grid." +
        "TableResultPanel inheritors could use 'configurator' constructor parameter to pass settings",
        grid));
    }
    return result;
  }

  public static boolean canInsertBlob(@NotNull DataGrid grid, @NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    int type = GridCellEditorHelper.get(grid).guessJdbcTypeForEditing(grid, row, column);
    return type == Types.BINARY || type == Types.BLOB || type == Types.LONGVARBINARY || type == Types.VARBINARY;
  }

  public static @Nullable TextCompletionProvider createCompletionProvider(@NotNull DataGrid grid,
                                                                          @NotNull ModelIndex<GridRow> row,
                                                                          @NotNull ModelIndex<GridColumn> column) {
    GridModel<GridRow, GridColumn> model = grid.getDataModel(DATA_WITH_MUTATIONS);
    GridColumn c = model.getColumn(column);
    if (c == null || !canComplete(grid, row, column)) return null;
    return new TextFieldWithAutoCompletion.StringsCompletionProvider(null, null) {
      @Override
      public String getPrefix(@NotNull String text, int offset) {
        return text;
      }

      @Override
      public @NotNull Collection<String> getItems(String prefix, boolean cached, CompletionParameters parameters) {
        List<String> items = GridCellEditorHelper.get(grid).getEnumValues(grid, column);
        if (!items.isEmpty()) return items;
        List<GridRow> rows = model.getRows();
        Set<String> objects = new HashSet<>(rows.size());
        for (GridRow r : rows) {
          Object v = c.getValue(r);
          if (v == null) {
            continue;
          }

          String value = Objects.requireNonNullElse(
            grid.getObjectFormatter().objectToString(c.getValue(r), c, createFormatterConfig(grid, column)),
            NULL_TEXT
          );

          objects.add(value);
        }
        return objects;
      }
    };
  }

  private static boolean canComplete(@NotNull DataGrid grid,
                                     @NotNull ModelIndex<GridRow> row,
                                     @NotNull ModelIndex<GridColumn> column) {
    GridModel<GridRow, GridColumn> model = grid.getDataModel(DATA_WITH_MUTATIONS);
    GridColumn c = model.getColumn(column);
    if (c == null || canInsertBlob(grid, row, column)) return false;
    if (ObjectFormatterUtil.isNumberType(c.getType())) return true;
    String className = c instanceof JdbcColumnDescriptor ? ((JdbcColumnDescriptor)c).getJavaClassName() : null;
    return className != null && !className.equals(CommonClassNames.JAVA_LANG_INTEGER) || ObjectFormatterUtil.isStringType(c.getType());
  }

  public static @Nullable DataGrid getDataGrid(DataContext dataContext) {
    FileEditor editor = PlatformCoreDataKeys.FILE_EDITOR.getData(dataContext);
    if (editor instanceof TableEditorBase) {
      return ((TableEditorBase)editor).getDataGrid();
    }
    DataGrid grid = editor == null ? null : editor.getUserData(GRID_KEY);
    return grid != null ? grid : DatabaseDataKeys.DATA_GRID_KEY.getData(dataContext);
  }

  public static boolean canInsertClob(@NotNull DataGrid grid, @NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column) {
    int type = GridCellEditorHelper.get(grid).guessJdbcTypeForEditing(grid, row, column);
    return type == Types.CLOB || type == Types.NCLOB || type == Types.LONGVARCHAR || type == Types.LONGNVARCHAR ||
           type == Types.NCHAR || type == Types.CHAR || type == Types.VARCHAR || type == Types.NVARCHAR || type == Types.SQLXML;
  }

  public static @NotNull LobInfo.FileBlobInfo blobFromFile(@NotNull VirtualFile virtualFile) {
    return new LobInfo.FileBlobInfo(new File(virtualFile.getPath()));
  }

  public static LobInfo.FileClobInfo clobFromFile(@NotNull VirtualFile virtualFile) {
    Charset encoding = EncodingManager.getInstance().getEncoding(virtualFile, true);
    String charset = encoding != null ? encoding.name() : null;
    File file = new File(virtualFile.getPath());
    return new LobInfo.FileClobInfo(file, charset);
  }

  public static @Nullable CellAttributesKey getMutationCellAttributes(@Nullable MutationType type) {
    if (type == null) return null;
    return switch (type) {
      case MODIFY -> CellColors.REPLACE;
      case INSERT -> CellColors.INSERT;
      case DELETE -> CellColors.REMOVE;
    };
  }

  public static @Nullable ActionCallback addRows(@NotNull DataGrid grid, int amount) {
    final GridMutator.RowsMutator<GridRow, GridColumn> mutator = getRowsMutator(grid);
    if (amount == 0 || mutator == null) return null;
    GridRequestSource source = newInsertOrCloneRowRequestSource(grid);
    if (mutator.isUpdateImmediately() && mutator.hasPendingChanges()) {
      grid.submit().doWhenDone(() -> mutator.insertRows(source, amount));
    }
    else {
      mutator.insertRows(source, amount);
    }
    return source.getActionCallback();
  }

  public static @Nullable ActionCallback addRow(@NotNull DataGrid grid) {
    return addRows(grid, 1);
  }

  public static GridRequestSource newInsertOrCloneRowRequestSource(@NotNull DataGrid grid) {
    GridRequestSource source = new GridRequestSource(new DataGridRequestPlace(grid));
    source.getActionCallback().doWhenDone(() -> {
      if (grid.getResultView().isEditing()) return;
      GridMutator.RowsMutator<GridRow, GridColumn> mutator = getRowsMutator(grid);
      ModelIndex<GridRow> row = mutator != null ? mutator.getLastInsertedRow() : ModelIndex.forRow(grid, -1);
      row = row != null && row.isValid(grid)
            ? row
            : ModelIndex.forRow(grid, grid.getDataModel(DATA_WITH_MUTATIONS).getRowCount() - 1);
      scrollToLocally(grid, row.toView(grid));
    });
    return source;
  }

  public static @NotNull @NlsSafe String extractSelectedValues(@NotNull DataGrid dataGrid, @NotNull DataExtractor extractor) {
    var out = new Out.Readable();
    extractValues(dataGrid, extractor, out, true, true);
    return out.getString();
  }

  public static @NotNull @NlsSafe String extractSelectedValuesForCopy(@NotNull DataGrid dataGrid, @NotNull DataExtractor extractor) {
    var out = new Out.Readable();
    GridHelper.get(dataGrid).extractValuesForCopy(dataGrid, extractor, out, true, true);
    return out.getString();
  }

  public static void extractSelectedValues(DataGrid dataGrid, DataExtractor extractor, Out out) {
    extractValues(dataGrid, extractor, out, true, true);
  }

  public static @NotNull Out extractValues(
    @NotNull DataGrid dataGrid,
    @NotNull DataExtractor extractor,
    @NotNull Out out,
    boolean selection,
    boolean transpositionAllowed
  ) {
    return GridHelper.get(dataGrid).extractValues(dataGrid, extractor, out, selection, transpositionAllowed);
  }

  public static @NlsContexts.ColumnName @NotNull String getRowName(@NotNull DataGrid grid, int relativeIndex) {
    GridDataHookUp<GridRow, GridColumn> dataHookup = grid.getDataHookup();
    boolean trueRows = dataHookup instanceof CsvDocumentDataHookUp && ((CsvDocumentDataHookUp)dataHookup).getFormat().rowNumbers;
    ModelIndex<GridRow> rowIndex = trueRows ? ViewIndex.forRow(grid, relativeIndex).toModel(grid) : ModelIndex.forRow(grid, relativeIndex);
    GridRow row = grid.getDataModel(DATA_WITH_MUTATIONS).getRow(rowIndex);
    if (row instanceof NamedRow) {
      return ((NamedRow)row).name;
    }
    if (row == null) return DataGridBundle.message("column.name.not.applicable");
    if (isInsertedRow(grid, rowIndex)) return String.valueOf(getInsertedRowIdx(grid, relativeIndex));
    GridRow previousRow = rowIndex.asInteger() == 0 ? null : grid.getDataModel(DATA_WITH_MUTATIONS).getRow(ModelIndex.forRow(grid, rowIndex.asInteger() - 1));
    return previousRow != null && previousRow.getRowNum() == row.getRowNum() ? "" : String.valueOf(row.getRowNum());
  }

  public static void suggestPlugin(@NotNull String id, @Nullable Project project) {
    new Task.Modal(null, IdeBundle.message("plugins.advertiser.task.searching.for.plugins"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        PluginNode descriptor = MarketplaceRequests.getInstance().getLastCompatiblePluginUpdate(PluginId.getId(id), null, indicator);
        if (descriptor == null) {
          LOG.error("Cannot find plugin " + id);
          return;
        }
        ApplicationManager.getApplication().invokeLater(() -> {
          try {
            new PluginsAdvertiserDialog(project, List.of(PluginDownloader.createDownloader(descriptor)), List.of(descriptor)).show();
          }
          catch (IOException e) {
            LOG.error(e);
          }
        });
      }
    }.queue();
  }

  public static void activeGridChanged(@Nullable DataGrid grid) {
    DataGrid.ActiveGridListener listener = activeGridListener();
    if (grid == null) {
      listener.closed();
      return;
    }
    listener.changed(grid);
  }

  public static DataGrid.ActiveGridListener activeGridListener() {
    return ApplicationManager.getApplication().getMessageBus().syncPublisher(DataGrid.ACTIVE_GRID_CHANGED_TOPIC);
  }

  public static boolean isIntervalModifierSet(@NotNull MouseEvent e) {
    return 0 != (e.getModifiersEx() & InputEvent.SHIFT_DOWN_MASK);
  }

  public static boolean isExclusiveModifierSet(@NotNull MouseEvent e) {
    return 0 != (e.getModifiersEx() & (InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK));
  }

  public static Pair<RelativePoint, Balloon.Position> getBestPositionForBalloon(@NotNull DataGrid grid) {
    ResultView view = grid.getResultView();
    RelativePoint point;
    Balloon.Position position = Balloon.Position.below;
    if (grid.getPresentationMode() == GridPresentationMode.TABLE) {
      point = JBPopupFactory.getInstance().guessBestPopupLocation(view.getComponent());
    }
    else if (view instanceof TreeTableResultView) {
      point = JBPopupFactory.getInstance().guessBestPopupLocation(((TreeTableResultView)view).getComponent().getTree());
    }
    else {
      JBLoadingPanel component = grid.getPanel().getComponent();
      point = new RelativePoint(component, new Point(component.getWidth() / 3, component.getHeight()));
      position = Balloon.Position.above;
    }
    return new Pair<>(point, position);
  }

  public static @NotNull Function<Integer, ObjectFormatterConfig> getConfigProvider(@NotNull DataGrid dataGrid) {
    return num -> {
      ModelIndex<GridColumn> idx = ModelIndex.forColumn(dataGrid, num);
      return createFormatterConfig(dataGrid, idx);
    };
  }

  public static boolean canMutateColumns(@Nullable DataGrid grid) {
    return grid != null && GridHelper.get(grid).canMutateColumns(grid);
  }

  public static ActionGroup getGridColumnHeaderPopupActions() {
    return (ActionGroup)ActionManager.getInstance().getAction("Console.TableResult.ColumnHeaderPopup");
  }

  public static void showErrorBalloon(@NotNull ErrorInfo errorInfo, final Component component, Point point)
  {
    if (component == null || !component.isVisible() || !component.isShowing()) {
      return;
    }
    String errorText = errorInfo.getMessage();
    errorText = wrap(errorText, 50, "\n", true);
    errorText = StringUtil.trimLog(errorText, 200);
    errorText = StringUtil.trimTrailing(errorText);

    final @NlsSafe StringBuilder balloonContent = new StringBuilder(errorText);
    HyperlinkListener hyperlinkListener = null;
    @NlsSafe
    Throwable throwable = errorInfo.getOriginalThrowable();
    if (throwable != null) {
      final String errorFullText = ExceptionUtil.getThrowableText(throwable, "com.intellij.");

      String detailsLinkMessage = " <a href=\"more\">" + DataGridBundle.message("message.hyperlink.click.for.more") + "</a>";
      balloonContent.append(detailsLinkMessage);

      hyperlinkListener = e -> {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          if ("more".equals(e.getDescription())) {
            Messages.showIdeaMessageDialog(null, errorFullText, DataGridBundle.message("dialog.title.query.error"), new String[]{CommonBundle.getOkButtonText()}, 0, Messages.getErrorIcon(), null);
          }
        }
      };
    }

    BalloonBuilder builder = JBPopupFactory
      .getInstance().createHtmlTextBalloonBuilder(balloonContent.toString(), MessageType.ERROR, hyperlinkListener).
        setClickHandler(e -> {
        }, true).
        setShowCallout(true).
        setHideOnAction(true).
        setHideOnClickOutside(true);
    final Balloon balloon = builder.createBalloon();

    balloon.show(new RelativePoint(component, point), Balloon.Position.below);
  }

  public static String wrap(@NotNull String str, int wrapLength, String newLineStr, boolean wrapLongWords) {
    if (newLineStr == null) {
      newLineStr = "\n";
    }
    if (wrapLength < 1) {
      wrapLength = 1;
    }
    int inputLineLength = str.length();
    int offset = 0;
    StringBuilder wrappedLine = new StringBuilder(inputLineLength + 32);

    while (inputLineLength - offset > wrapLength) {
      if (str.charAt(offset) == ' ') {
        offset++;
        continue;
      }
      int spaceToWrapAt = str.lastIndexOf(' ', wrapLength + offset);

      if (spaceToWrapAt >= offset) {
        // normal case
        wrappedLine.append(str, offset, spaceToWrapAt);
        wrappedLine.append(newLineStr);
        offset = spaceToWrapAt + 1;
      }
      else {
        // really long word or URL
        if (wrapLongWords) {
          // wrap really long word one line at a time
          wrappedLine.append(str, offset, wrapLength + offset);
          wrappedLine.append(newLineStr);
          offset += wrapLength;
        }
        else {
          // do not wrap really long word, just extend beyond limit
          spaceToWrapAt = str.indexOf(' ', wrapLength + offset);
          if (spaceToWrapAt >= 0) {
            wrappedLine.append(str, offset, spaceToWrapAt);
            wrappedLine.append(newLineStr);
            offset = spaceToWrapAt + 1;
          }
          else {
            wrappedLine.append(str.substring(offset));
            offset = inputLineLength;
          }
        }
      }
    }

    // Whatever is left in line is short enough to just pass through
    wrappedLine.append(str.substring(offset));

    return wrappedLine.toString();
  }

  public static @NotNull TableDataParseResult retrieveDataFromText(@NotNull Project project, @NotNull String text, @NotNull DataGrid grid) {
    GridHelper helper = GridHelper.get(grid);
    ChoosePasteFormatAction.PasteType type = ChoosePasteFormatAction.PasteType.get();
    Pair<List<String[]>, CsvFormat> res = type.getParser().parse(project, text);
    List<String[]> parsed = res.getFirst();
    CsvFormat format = res.getSecond();
    List<DataTypeConversion.Builder> conversions = new ArrayList<>();
    List<GridColumn> columns = new ArrayList<>();
    for (int i = 0; i < parsed.size(); i++) {
      String[] record = parsed.get(i);
      for (int j = 0; j < record.length; j++) {
        String value = record[j];
        if (columns.size() <= j) {
          columns.add(new DataConsumer.Column(j, "dummy", Types.VARCHAR, "text", String.class.getName()));
        }
        GridColumn column = columns.get(j);
        conversions.add(createBuilder(i, column, value, helper));
      }
    }
    return new TableDataParseResult(new GridTransferableData(conversions, new TextTransferable(text), 0, 0, parsed.size()), type, format);
  }

  private static @NotNull DataTypeConversion.Builder createBuilder(int row, @NotNull GridColumn column, Object value, @NotNull GridHelper helper) {
    return helper.createDataTypeConversionBuilder()
      .firstColumn(column)
      .firstRowIdx(row)
      .firstColumnIdx(column.getColumnNumber())
      .firstGrid(null)
      .value(value);
  }

  public static @Nullable VirtualFile getVirtualFile(@Nullable CoreGrid<GridRow, GridColumn> grid) {
    return grid == null ? null : getVirtualFile(grid.getDataHookup());
  }

  public static @Nullable VirtualFile getVirtualFile(@Nullable GridDataHookUp<?, ?> hookup) {
    return hookup instanceof HookUpVirtualFileProvider ? ((HookUpVirtualFileProvider)hookup).getVirtualFile() : null;
  }

  public static @NotNull PsiElement getPsiElementForSelection(@NotNull DataGrid grid) {
    SelectionModel<GridRow, GridColumn> selectionModel = grid.getSelectionModel();
    if (selectionModel.isSelectionEmpty()) {
      return DataGridPomTarget.wrapDataGrid(grid.getProject(), grid);
    }
    return DataGridPomTarget.wrapCell(grid.getProject(), grid,
                                      selectionModel.getSelectedRows(),
                                      selectionModel.getSelectedColumns());
  }

  public static ActionGroup getGridPopupActions() {
    return (ActionGroup)ActionManager.getInstance().getAction("Console.TableResult.PopupGroup");
  }

  public static @Nullable DocumentDataHookUp getDocumentDataHookUp(@NotNull CoreGrid<GridRow, GridColumn> grid) {
    return getHookUp(grid, DocumentDataHookUp.class);
  }

  public static @NotNull JComponent addGridHeaderComponent(@NotNull DataGrid dataGrid) {
    return addGridHeaderComponent(dataGrid, false, "Console.EditorTableResult.Group", "Console.TableResult.Group.Secondary");
  }

  public static @NotNull JComponent addGridHeaderComponent(@NotNull DataGrid dataGrid, boolean transparent,
                                                           @Nullable String actionGroupName,
                                                           @NotNull String secondaryActionsGroupName) {
    ActionManager actionManager = ActionManager.getInstance();
    ActionGroup actions = actionGroupName == null ? new EmptyActionGroup() : (ActionGroup)actionManager.getAction(actionGroupName);
    ActionGroup secondaryActions = (ActionGroup)actionManager.getAction(secondaryActionsGroupName);
    return addGridHeaderComponent(dataGrid, transparent, actions, secondaryActions);
  }

  public static @NotNull JComponent addGridHeaderComponent(@NotNull DataGrid dataGrid, boolean transparent,
                                                           ActionGroup actions,
                                                           ActionGroup secondaryActions) {
    ActionManager actionManager = ActionManager.getInstance();
    ActionToolbar toolbar = actionManager.createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, actions, true);
    ActionToolbar toolbarSecondary = actionManager.createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, secondaryActions, true);
    toolbar.setTargetComponent(dataGrid.getPanel().getComponent());
    toolbarSecondary.setTargetComponent(dataGrid.getPanel().getComponent());
    toolbarSecondary.setReservePlaceAutoPopupIcon(false);
    JComponent header = new TwoSideComponent(toolbar.getComponent(), toolbarSecondary.getComponent());
    Insets insets = JBUI.CurrentTheme.Toolbar.horizontalToolbarInsets();
    Border border = insets == null ? JBUI.Borders.empty(1, 0, 0, 5) : JBUI.Borders.empty(insets.top, insets.left, insets.bottom - 1, insets.right);
    header.setBorder(new CompoundBorder(JBUI.Borders.customLine(JBUI.CurrentTheme.Editor.BORDER_COLOR, 0, 0, 1, 0), border));
    toolbar.getComponent().setBorder(JBUI.Borders.empty());
    toolbarSecondary.getComponent().setBorder(JBUI.Borders.empty());

    header.setOpaque(!transparent);
    toolbar.getComponent().setOpaque(!transparent);
    toolbarSecondary.getComponent().setOpaque(!transparent);

    dataGrid.getPanel().setTopComponent(header);

    if (dataGrid.isFilteringSupported()) {
      dataGrid.getPanel().setSecondTopComponent(dataGrid.getFilterComponent().getComponent());
    }

    return header;
  }

  public static @NotNull JComponent addVerticalGridHeaderComponent(@NotNull DataGrid dataGrid, @Nullable String actionGroupName) {
    ActionManager actionManager = ActionManager.getInstance();
    ActionGroup actions = actionGroupName == null ? new EmptyActionGroup() : (ActionGroup)actionManager.getAction(actionGroupName);
    ActionToolbar toolbar = actionManager.createActionToolbar(ActionPlaces.EDITOR_TOOLBAR, actions, false);
    toolbar.setTargetComponent(dataGrid.getPanel().getComponent());

    toolbar.getComponent().setOpaque(false);
    Insets insets = toolbar.getComponent().getBorder().getBorderInsets(toolbar.getComponent());
    toolbar.getComponent().setBorder(new JBEmptyBorder(0, insets.left, 0, insets.right));

    dataGrid.getPanel().setRightHeaderComponent(toolbar.getComponent());

    if (dataGrid.isFilteringSupported()) {
      dataGrid.getPanel().setSecondTopComponent(dataGrid.getFilterComponent().getComponent());
    }

    return toolbar.getComponent();
  }

  public static FileEditor getOrCreateEditorWrapper(@NotNull DataGrid resultPanel,
                                                    @NotNull Project project,
                                                    @NotNull Supplier<@Nls String> getName) {
    JComponent dataGridComponent = resultPanel.getPanel().getComponent();
    TableEditorBase editor = ClientProperty.get(dataGridComponent, FILE_EDITOR_KEY);
    if (editor == null) {
      editor = new TableEditorBase(project) {
        private final LightVirtualFile myFile = new LightVirtualFile(getName(), TableDataFragmentFile.MyFileType.INSTANCE, "");

        @Override
        public void dispose() {
          super.dispose();
          myFile.setValid(false);
        }

        @Override
        public VirtualFile getFile() {
          return myFile;
        }

        @Override
        public boolean isValid() {
          return myFile.isValid();
        }

        @Override
        public @NotNull DataGrid getDataGrid() {
          return resultPanel;
        }

        @Override
        public @NotNull String getName() {
          String name = getName.get();
          return name != null ? name : super.getName();
        }
      };
      dataGridComponent.putClientProperty(FILE_EDITOR_KEY, editor);
      Disposer.register(resultPanel, editor);
    }
    return editor;
  }

  public static @NlsContexts.NotificationContent @NotNull String getContent(@NlsContexts.NotificationContent @NotNull String content, @NotNull String path) {
    return content +
           (StringUtil.isEmpty(content) ? "" : " ") +
           wrapInOpenFileLink(path);
  }

  public static void configureFullSizeTable(@NotNull DataGrid grid, @NotNull DataGridAppearance appearance) {
    appearance.setResultViewAdditionalRowsCount(ADDITIONAL_ROWS_COUNT);
    appearance.setResultViewSetShowHorizontalLines(true);
    appearance.setResultViewStriped(DataGridAppearanceSettings.getSettings().isStripedTable());
    appearance.setTransparentRowHeaderBackground(true);
  }

  public static void withFloatingPaging(@NotNull DataGrid grid, @NotNull DataGridAppearance appearance) {
    grid.putUserData(FloatingPagingManager.AVAILABLE_FOR_GRID_TYPE, true);
  }

  public static @NotNull DataGrid createDataGrid(@NotNull Project project,
                                                 @NotNull GridDataHookUp<GridRow, GridColumn> dataHookUp,
                                                 @NotNull ActionGroup popupActions,
                                                 @NotNull BiConsumer<DataGrid, DataGridAppearance> configure) {
    return createDataGrid(project, dataHookUp, popupActions, configure, false);
  }

  public static @NotNull DataGrid createDataGrid(@NotNull Project project,
                                                 @NotNull GridDataHookUp<GridRow, GridColumn> dataHookUp,
                                                 @NotNull ActionGroup popupActions,
                                                 @NotNull BiConsumer<DataGrid, DataGridAppearance> configure,
                                                 boolean isHierarchicalGrid) {
    if (isHierarchicalGrid) {
      return new HierarchicalTableResultPanel(project, dataHookUp, popupActions, configure);
    }
    else {
      return new TableResultPanel(project, dataHookUp, popupActions, configure);
    }
  }

  public static @Nullable String getEditorTabName(@NotNull DataGrid grid) {
    VirtualFile file = getVirtualFile(grid);
    return file == null ? null : file.getNameWithoutExtension();
  }

  public static class FileNotificationListener implements NotificationListener {
    private final String myPath;
    private final Project myProject;

    public FileNotificationListener(@NotNull Project project, @NotNull String path) {
      myPath = path;
      myProject = project;
    }

    @Override
    public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
      if (!OPEN_FILE_DESC.equals(event.getDescription()) || event.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;
      File file = new File(myPath);
      ApplicationManager.getApplication().executeOnPooledThread(() -> {
        VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
        if (virtualFile == null) {
          DataGridNotifications.EXTRACTORS_GROUP.createNotification(
            DataGridBundle.message("notification.content.can.t.access.file", myPath),
            NotificationType.WARNING
          ).setDisplayId("FileNotificationListener.cant.access").notify(myProject);
          return;
        }
        VfsUtil.markDirtyAndRefresh(false, false, false, virtualFile);
        ApplicationManager.getApplication().invokeLater(() -> {
          showFile(myProject, virtualFile);
        });
      });
    }

    private static void showFile(@NotNull Project project, @NotNull VirtualFile virtualFile) {
      if (virtualFile.isDirectory()) {
        RevealFileAction.openDirectory(VfsUtilCore.virtualToIoFile(virtualFile));
        return;
      }
      if (SingleRootFileViewProvider.isTooLargeForIntelligence(virtualFile) || virtualFile.getFileType().isBinary()) {
        RevealFileAction.openFile(VfsUtilCore.virtualToIoFile(virtualFile));
        return;
      }
      FileEditorManager.getInstance(project).openEditor(new OpenFileDescriptor(project, virtualFile), true);
    }
  }

  public static boolean collapseColumnsSubtree(@NotNull DataGrid grid,
                                               @NotNull ModelIndex<GridColumn> columnIdx,
                                               int subtreeRootDepth) {
    return collapseColumnsSubtree(grid, columnIdx, subtreeRootDepth, null);
  }

  public static boolean collapseColumnsSubtree(@NotNull DataGrid grid,
                                               @NotNull ModelIndex<GridColumn> columnIdx,
                                               int subtreeRootDepth,
                                               @Nullable Runnable onCollapseCompleted) {
    GridModel<?, GridColumn> model = grid.getDataModel(DATA_WITH_MUTATIONS);
    HierarchicalReader hierarchicalReader = model.getHierarchicalReader();
    if (hierarchicalReader == null) return false;

    GridColumn column = model.getColumn(columnIdx);
    if (column == null) return false;

    if (!(column instanceof HierarchicalGridColumn hierarchicalColumn)) {
      return false;
    }

    HierarchicalGridColumn ancestor =
      hierarchicalReader.getAncestorAtDepth(hierarchicalColumn, subtreeRootDepth);
    if (ancestor == null || ancestor.getChildren().isEmpty()) return false;

    List<HierarchicalGridColumn> children = hierarchicalReader.getAllLeafNodesInSubtree(ancestor);

    HierarchicalColumnsCollapseManager collapseManager = grid.getHierarchicalColumnsCollapseManager();
    if (collapseManager == null) return false;

    boolean shouldCollapse = !collapseManager.isColumnCollapsedSubtree(columnIdx), shouldExpand = !shouldCollapse;
    for (ModelIndex<GridColumn> idx : grid.getDataModel(DATA_WITH_MUTATIONS).getColumnIndices().asIterable()) {
      GridColumn c = model.getColumn(idx);
      if (c == null) continue;
      if (!children.contains(c)) continue;

      if (c.equals(column)) {
        collapseManager.setIsCollapsedSubtree(column, shouldCollapse);
        continue;
      }

      if (shouldExpand && collapseManager.isColumnCollapsedSubtree(c)) {
        collapseManager.setIsCollapsedSubtree(c, false);
      }
      collapseManager.setIsHiddenDueToCollapse(c, shouldCollapse);

      if (!grid.isColumnEnabled(idx)) continue;

      grid.getResultView().setColumnEnabled(
        ModelIndex.forColumn(model, c.getColumnNumber()),
        shouldExpand
      );
    }

    if (onCollapseCompleted != null) {
      onCollapseCompleted.run();
    }

    // Use columnAttributesUpdated here because we need to recreate
    // Swing columns in the TableResultView immediately after collapsing.
    // The fireContentChanged method only does it after some time.
    grid.getResultView().columnAttributesUpdated();

    return true;
  }

  public static @NotNull String getText(@NotNull DataGrid grid, @NotNull ModelIndex<GridRow> rowIdx, @NotNull ModelIndex<GridColumn> columnIdx) {
    return getText(grid, rowIdx, columnIdx, DATA_WITH_MUTATIONS);
  }

  public static @NotNull String getText(@NotNull DataGrid grid, @NotNull ModelIndex<GridRow> rowIdx, @NotNull ModelIndex<GridColumn> columnIdx,
                                        @NotNull DataAccessType dataAccessType) {
    GridModel<GridRow, GridColumn> model = grid.getDataModel(dataAccessType);
    GridRow row = model.getRow(rowIdx);
    if (row == null) {
      return NULL_TEXT;
    }
    GridColumn column = model.getColumn(columnIdx);
    if (column == null) {
      return NULL_TEXT;
    }
    return getText(grid, row, column, createFormatterConfig(grid, columnIdx));
  }

  public static @NotNull String getText(@NotNull DataGrid grid, @NotNull GridRow row, @NotNull GridColumn column, @NotNull DatabaseDisplayObjectFormatterConfig config) {
    String value = grid.getObjectFormatter().objectToString(column.getValue(row), column, config);
    return Objects.requireNonNullElse(value, NULL_TEXT);
  }

  public static class GridRevealFileAction extends AnAction {
    private final String myPath;

    public GridRevealFileAction(@NotNull String path) {
      super(RevealFileAction.getActionName());
      myPath = path;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      RevealFileAction.openFile(new File(myPath));
    }
  }

  public static @Nullable HierarchicalGridColumn getClosestAncestorWithSelectedDirectLeaf(@NotNull DataGrid grid, @NotNull HierarchicalGridColumn leaf) {
    for (HierarchicalGridColumn currentNode = leaf; currentNode != null; currentNode = currentNode.getParent()) {
      if (isAnyDirectLeafChildSelected(grid, currentNode)) {
        return currentNode;
      }
    }

    return null;
  }

  public static @Nullable HierarchicalGridColumn getLastAncestorWithSelectedDirectLeaf(
    @NotNull DataGrid grid,
    @NotNull HierarchicalGridColumn startingNode
  ) {
    HierarchicalGridColumn highestAncestorWithSelectedChild = null;
    for (HierarchicalGridColumn currentNode = startingNode; currentNode != null; currentNode = currentNode.getParent()) {
      if (isAnyDirectLeafChildSelected(grid, currentNode)) {
        highestAncestorWithSelectedChild = currentNode;
      }
    }

    return highestAncestorWithSelectedChild;
  }

  /**
   * Determines if any direct child leaf of the given currentNode is selected within the grid.
   * Note:
   * 1. Only leaf columns can be selected, hence there's a check to ensure we're only considering leaf nodes.
   * 2. By design, this method doesn't perform recursive checks on non-leaf children. It solely focuses on direct children of the currentNode.
   */
  private static boolean isAnyDirectLeafChildSelected(
    @NotNull DataGrid grid,
    @NotNull HierarchicalGridColumn currentNode
  ) {
    return ContainerUtil.exists(currentNode.getChildren(), child -> {
      if (!child.isLeaf()) return false;
      ModelIndex<GridColumn> childModelIdx = ModelIndex.forColumn(grid, child.getColumnNumber());
      return grid.getSelectionModel().isSelectedColumn(childModelIdx);
    });
  }
}
