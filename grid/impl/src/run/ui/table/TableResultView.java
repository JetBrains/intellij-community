package com.intellij.database.run.ui.table;

import com.intellij.database.DatabaseDataKeys;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.DataGridListener;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridColumnLayout;
import com.intellij.database.datagrid.GridHelper;
import com.intellij.database.datagrid.GridRequestSource;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.GridUtil;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.datagrid.ModelIndexSet;
import com.intellij.database.datagrid.RawIndexConverter;
import com.intellij.database.datagrid.ResultView;
import com.intellij.database.datagrid.ResultViewColumn;
import com.intellij.database.datagrid.SelectionModel;
import com.intellij.database.datagrid.ViewIndex;
import com.intellij.database.datagrid.ViewIndexSet;
import com.intellij.database.extractors.DisplayType;
import com.intellij.database.run.ui.ColumnMove;
import com.intellij.database.run.ui.ColumnOrderRestorer;
import com.intellij.database.run.ui.GridTableCellEditor;
import com.intellij.database.run.ui.ResultViewWithCells;
import com.intellij.database.run.ui.ResultViewWithColumns;
import com.intellij.database.run.ui.ResultViewWithFrozenColumns;
import com.intellij.database.run.ui.ResultViewWithRows;
import com.intellij.database.run.ui.TableAggregatorWidgetHelper;
import com.intellij.database.run.ui.grid.ColumnLayoutContext;
import com.intellij.database.run.ui.grid.DataGridSearchSession;
import com.intellij.database.run.ui.grid.DummyGridColumnLayout;
import com.intellij.database.run.ui.grid.GridRowHeader;
import com.intellij.database.run.ui.grid.GridSearchSession;
import com.intellij.database.run.ui.grid.TableCellImageCache;
import com.intellij.database.run.ui.table.statisticsPanel.StatisticsPanelMode;
import com.intellij.database.run.ui.table.statisticsPanel.StatisticsTableHeader;
import com.intellij.database.settings.DataGridSettings;
import com.intellij.database.settings.DataGridSettings.AutoTransposeMode;
import com.intellij.database.util.GridActionUiState;
import com.intellij.find.FindModel;
import com.intellij.find.SearchSession;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.TableUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.hover.TableHoverListener;
import com.intellij.ui.table.AdditionalTableHeader;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.CellRendererPane;
import javax.swing.DefaultListSelectionModel;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EventObject;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The complete table view, including any pinned columns.
 * <p>
 * The view sets up every table the same way and owns the state they share. That is the grid's columns, the cell image cache, the data for
 * grid actions, sorting, transposing, the statistics header, and writing what cell editors commit. Each table shows its share of the view's
 * column model through a slice: the main table the unpinned columns, the pinned table the pinned ones. The grid's index converter and
 * selection model take the columns from the view's column model.
 * <p>
 * A call that reaches only the main table is correct for one of two reasons. Either the pinned table follows it, since it shares the data
 * model, the row order, the row selection and the view's column model. That column model's selection and order are those of both tables. Or
 * the call reads rows, geometry or appearance that are the same in both tables.
 */
public final class TableResultView implements ResultView, ResultViewWithCells, ResultViewWithColumns, ResultViewWithRows,
                                              ResultViewWithFrozenColumns, EditorColorsListener, UISettingsListener {
  private static final Logger LOG = Logger.getInstance(TableResultView.class);

  private final DataGrid myResultPanel;
  private final GridColumns myColumns;
  /**
   * A slice of every column of the view's column model, which no table displays, for the grid's selection listeners. Through it they hear
   * of a column selection change in any table, and of a column move once, after the move. Created before the main table's slice, so the
   * listeners hear of a change after the main table.
   */
  private final GridColumnSlice myGridSelectionEvents;
  /** The columns the main table shows: the unpinned columns of the view's column model. */
  private final GridColumnSlice myPrimaryColumns;
  /** The main table, the pinned table while a visible column is pinned, and the columns each shows. Only the view changes it. */
  private final ViewTables myTables;
  private final ActionGroup myColumnActions;
  private final ActionGroup myRowActions;
  /** Mouse listeners for every table of the view, which a pinned table created later gets too. */
  private final List<MouseListener> myComponentMouseListeners = new ArrayList<>();
  /** Applies a header drag in the pinned table to the grid's data, or null while there is no pinned table. */
  private @Nullable MoveColumnListener myPinnedMoveColumnListener;
  private final GridTable myPrimary;
  /** The grid's index converter: the columns of the view's column model and the rows of the main table. */
  private final TableRawIndexConverter myRawIndexConverter;
  private final FrozenColumnsController myFrozenColumnsController;
  private final TableFloatingToolbarController myFloatingToolbar;
  private final TableScrollPane myScrollPane;
  private GridColumnLayout<GridRow, GridColumn> myColumnLayout = new DummyGridColumnLayout();
  private StatisticsTableHeader myStatisticsHeader;
  private StatisticsHeaderHost myStatisticsHeaderHost;
  /** The value of the open multi-cell edit, which both tables show in the selected cells. */
  private final MultiCellEditValue myEditedValue = new MultiCellEditValue();
  private ModelIndex<GridColumn> myContextColumn;
  private final GridSelectionGrower myGrower;
  private boolean myWasAutomaticallyTransposed;
  private final TableCellImageCache myCellImageCache;
  private final RowHeightModel myRowHeights = new RowHeightModel();
  /** The data model both tables show, which the view replaces on a transpose. */
  private GridTableModel myDataModel;
  /** The row selection both tables share. */
  private final ListSelectionModel myRowSelection = new DefaultListSelectionModel();
  private final TableRowOrder myRowOrder;
  /** What Escape does in every table of the view, once the grid has registered it. */
  private @Nullable AbstractAction myEscapeAction;
  /** Whether a check that the focus follows the lead is waiting for the event queue. */
  private boolean myFocusCheckQueued;
  private boolean myDisposed;
  private boolean myShowRowNumbers;
  /** The row header the scroll pane shows, beside the pinned table while one shows; this view creates and disposes it. */
  private @Nullable GridRowHeader myRowHeader;

  public TableResultView(@NotNull DataGrid grid, @NotNull ActionGroup columnActions, @NotNull ActionGroup rowActions) {
    myResultPanel = grid;
    myContextColumn = ModelIndex.forColumn(grid, -1);
    myDataModel = new RegularGridTableModel(grid);
    myColumns = new GridColumns(grid, new ColumnsHost(), myDataModel);
    // Created after GridColumns, which then hears of a column move after the slices and the main table.
    myGridSelectionEvents = new GridColumnSlice(myColumns.getColumnModel(), myColumns, ColumnsToShow.ALL);
    myPrimaryColumns = new GridColumnSlice(myColumns.getColumnModel(), myColumns, ColumnsToShow.UNPINNED);
    myPrimary = new GridTable(grid, myDataModel, myRowSelection, myPrimaryColumns, myColumns.getColumnCache(), columnActions,
                              rowActions);
    myRawIndexConverter = new TableRawIndexConverter(myPrimary, myColumns.getColumnModel());
    myTables = new ViewTables(grid, myPrimary, myPrimaryColumns);
    myColumnActions = columnActions;
    myRowActions = rowActions;
    myRowOrder = new TableRowOrder(grid, myRowSelection, myColumns.getColumnModel(), myRowHeights, myTables);
    // One cache for the cell images of every table of the view.
    myCellImageCache = new TableCellImageCache(this, this);
    myFloatingToolbar = new TableFloatingToolbarController(grid, grid.getCoroutineScope());
    myFrozenColumnsController = new FrozenColumnsController(myTables, myColumns.getColumnModel(), myRowSelection, grid);
    myPrimary.installHost(adoptTable(myPrimary), myRowHeights, myCellImageCache, myEditedValue, myRowOrder.getSorter());
    // Installed on the main table, where the grid looks for it; it selects the columns of the view's column model.
    TableSelectionModel.install(myPrimary, myColumns.getColumnModel(), myRawIndexConverter, grid);
    myPrimary.installGridIntegration(createMoveColumnListener());
    myScrollPane = TableResultViewFactory.createScrollPane(grid, myPrimary, this);
    myGrower = new GridSelectionGrower(grid, this);
    // Both tables select through the view's column model, so this covers both.
    myColumns.getColumnModel().getSelectionModel().addListSelectionListener(e -> myGrower.reset());
    // The main table scrolls to the lead in its own columns only; a lead in the pinned table still needs its row in view.
    ListSelectionListener pinnedLeadRowReveal = e -> {
      if (!e.getValueIsAdjusting()) myFrozenColumnsController.revealPinnedLeadRow();
    };
    myGridSelectionEvents.getSelectionModel().addListSelectionListener(pinnedLeadRowReveal);
    myRowSelection.addListSelectionListener(pinnedLeadRowReveal);
    // A lead that moves into the other table, as with Find Next or a header click there, takes the focus along.
    // A column move or hide shifts the lead before the slices follow, so the lead's table is known only afterwards.
    myColumns.getColumnModel().getSelectionModel().addListSelectionListener(e -> {
      if (e.getValueIsAdjusting() || myFocusCheckQueued) return;
      myFocusCheckQueued = true;
      ApplicationManager.getApplication().invokeLater(() -> {
        myFocusCheckQueued = false;
        moveFocusToLeadColumnTable();
      }, ModalityState.any(), ignored -> myDisposed);
    });
    // Focus given back to the main table, as after Find, goes on to the pinned table while it shows the lead cell.
    myPrimary.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        GridTable leadTable = leadColumnTable();
        if (leadTable != myPrimary && !e.isTemporary()) {
          IdeFocusManager.getInstance(grid.getProject()).requestFocus(leadTable, false);
        }
      }
    });
    TableAggregatorWidgetHelper.install(this, grid);
    TableGoToRowHelper.install(this, grid);
    TablePositionWidgetHelper.install(this, grid);
    grid.addDataGridListener(new DataGridListener() {
      @Override
      public void onValueEdited(DataGrid dataGrid, @Nullable Object object) {
        myEditedValue.setValue(Ref.create(object));
      }

      @Override
      public void onContentChanged(DataGrid dataGrid, @Nullable GridRequestSource.RequestPlace place) {
        resizeRowHeader();
      }
    }, this);
    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      initializeColumnLayout();
    }
  }

  private void initializeColumnLayout() {
    myColumnLayout = GridHelper.get(myResultPanel).createColumnLayout(new TableColumnLayoutContext(), myResultPanel);
    myPrimary.installColumnLayout(myColumnLayout);
  }

  @TestOnly
  void initializeColumnLayoutForTests() {
    initializeColumnLayout();
  }

  /** Sets up a table of this view, the main or the pinned one, and creates the host it reports to. */
  private GridTableHost adoptTable(GridTable table) {
    myFloatingToolbar.attach(table);
    if (myEscapeAction != null) table.registerEscapeAction(myEscapeAction);
    table.getEmptyText().setText(""); //DatabaseMessages.message("table.result.data.loading"));
    table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    table.setCellSelectionEnabled(true);
    table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    table.setBackground(myResultPanel.getColorsScheme().getDefaultBackground());
    // fixes missing (or plain white) grid on mac
    table.setShowGrid(true);
    table.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(@NotNull MouseEvent e) {
        if (!SwingUtilities.isRightMouseButton(e)) return;
        int rowIndex = table.isTransposed() ? table.columnAtPoint(e.getPoint()) : table.rowAtPoint(e.getPoint());
        int columnIndex = table.isTransposed() ? table.rowAtPoint(e.getPoint()) : table.columnAtPoint(e.getPoint());
        if (rowIndex < 0 || columnIndex < 0 || table.isCellSelected(rowIndex, columnIndex)) return;
        int modelRow = table.getRawIndexConverter().row2Model().applyAsInt(rowIndex);
        int modelColumn = table.getRawIndexConverter().column2Model().applyAsInt(columnIndex);
        myResultPanel.getSelectionModel().setSelection(ModelIndexSet.forRows(myResultPanel, modelRow),
                                                       ModelIndexSet.forColumns(myResultPanel, modelColumn));
      }
    });
    return new TableHost(table);
  }

  /** Moves a column dragged in the header of either table in the grid's data too, reading the order from the view's column model. */
  private @NotNull MoveColumnListener createMoveColumnListener() {
    return new MoveColumnListener(myResultPanel, myPrimary, myColumns, myRowOrder);
  }

  private @Nullable GridTable getFrozenView() {
    return myTables.getPinned();
  }

  private List<GridTable> tables() {
    return myTables.all();
  }

  private void forEachTable(Consumer<GridTable> operation) {
    for (GridTable table : myTables.all()) operation.accept(table);
  }

  @Override
  public @NotNull JComponent getComponent() { return myPrimary; }

  /** The pinned table shows cells of the view too. */
  @Override
  public boolean hasFocus() {
    for (GridTable table : tables()) {
      if (table.hasFocus()) return true;
    }
    return false;
  }

  /** The scroll pane that holds both tables. {@link TableResultViewFactory#wrap} places it in the grid layout. */
  @NotNull TableScrollPane getScrollPane() { return myScrollPane; }

  /** A pinned table created later gets it too. */
  @Override
  public void registerEscapeAction(@NotNull AbstractAction action) {
    myEscapeAction = action;
    forEachTable(table -> table.registerEscapeAction(action));
  }

  @Override
  public boolean isEditing() { return editingTable() != null; }

  private @Nullable GridTable editingTable() {
    if (myPrimary.isEditing()) return myPrimary;
    GridTable frozen = getFrozenView();
    return frozen != null && frozen.isEditing() ? frozen : null;
  }

  @Override
  public @Nullable Component getEditorComponent() {
    GridTable editing = editingTable();
    return editing == null ? null : editing.getEditorComponent();
  }

  @Override
  public @Nullable TableCellEditor getCellEditor() {
    GridTable editing = editingTable();
    return editing == null ? null : editing.getCellEditor();
  }

  /**
   * Takes the pins of the grid. Each slice picks its columns again, so a pinned column moves between the main table and the pinned one. It
   * keeps its place in the view's column model. The pinned table comes with its first visible pinned column and goes with the last one.
   */
  @Override
  public void setFrozenColumns(@NotNull Set<ModelIndex<GridColumn>> columns) {
    // A column that moves to the other table would take the open editor's cell out of its table.
    if (isEditing() && !stopEditing()) cancelEditing();
    myColumns.setPinnedColumns(columns);
    myTables.pinsChanged();
    updatePinnedTable();
  }

  /**
   * Creates the pinned table for the first visible pinned column and drops it with the last one, without changing the pins. Runs after each
   * change of the pins, and after each column operation that shows, hides or rebuilds columns. The slices follow those on their own, but a
   * pinned column that comes back with no pinned table has no table to show it.
   */
  private void updatePinnedTable() {
    myColumns.lockNewlyShownPinnedColumns();
    if (!myColumns.hasVisiblePinnedColumns()) {
      dropPinnedTable();
      return;
    }
    // A table created after dispose would stay registered for disposal with nothing to dispose it.
    if (myDisposed) return;
    GridTable pinned = myTables.getPinned();
    if (pinned == null) pinned = addPinnedTable();
    // Again after each change, so the pinned table follows the main table's fonts, lines and row height.
    pinned.applyAppearance(myPrimary.captureAppearance());
    myFrozenColumnsController.showPinnedTable();
  }

  /**
   * Creates the pinned table, with a slice of its own. The setup clears the selection, so the table is set up on an empty row selection and
   * column model. Then it gets the view's row selection and its slice. Both tables share the data model, the row order, the row selection
   * and, through their slices, the view's column model.
   */
  private @NotNull GridTable addPinnedTable() {
    GridColumnSlice columns = new GridColumnSlice(myColumns.getColumnModel(), myColumns, ColumnsToShow.PINNED);
    FrozenStripTable table = new FrozenStripTable(myResultPanel, myDataModel, new DefaultListSelectionModel(),
                                                  new DefaultTableColumnModel(), myColumns.getColumnCache(), myColumnActions,
                                                  myRowActions);
    table.installHost(adoptTable(table), myRowHeights, myCellImageCache, myEditedValue, myRowOrder.getSorter());
    table.setSelectionModel(myRowSelection);
    table.setColumnModel(columns);
    for (MouseListener listener : myComponentMouseListeners) table.addMouseListener(listener);
    // Mutable document grids update their source on mouse release, when the slice has moved the column in the view's column model.
    MoveColumnListener moveColumnListener = createMoveColumnListener();
    table.getTableHeader().addMouseListener(moveColumnListener);
    columns.addColumnModelListener(moveColumnListener);
    myPinnedMoveColumnListener = moveColumnListener;
    myTables.pinnedTableAdded(table, columns);
    return table;
  }

  /**
   * Drops the pinned table once no visible column is pinned, and undoes {@link #adoptTable} for it. Its slice goes too; the next
   * pinned table gets a new one. The cell image cache holds the table's renderers, so it starts over for the table that is left.
   */
  private void dropPinnedTable() {
    GridTable table = myTables.getPinned();
    if (table == null) return;
    myFrozenColumnsController.pinnedTableDropping();
    GridColumnSlice columns = Objects.requireNonNull(myTables.pinnedTableDropped());
    MoveColumnListener moveColumnListener = myPinnedMoveColumnListener;
    myPinnedMoveColumnListener = null;
    if (moveColumnListener != null) {
      table.getTableHeader().removeMouseListener(moveColumnListener);
      columns.removeColumnModelListener(moveColumnListener);
    }
    for (MouseListener listener : myComponentMouseListeners) table.removeMouseListener(listener);
    // JTable and TableExpandableItemsHandler both listen to the row selection; a model of its own lets each of them unregister.
    table.setSelectionModel(new DefaultListSelectionModel());
    // The same for the slice, before it goes: a new model clears the column selection, which through the slice is the view's.
    table.setColumnModel(new DefaultTableColumnModel());
    table.detachModel();
    Disposer.dispose(table);
    myFloatingToolbar.detach(table);
    columns.dispose();
    myCellImageCache.reset();
    updateCellImageCacheSize();
  }

  @Override
  public void showRowNumbers(boolean show) {
    myShowRowNumbers = show;
    setRowHeader(!showsRowHeader() ? null : myRowHeader != null ? myRowHeader : createRowHeader());
  }

  /** Whether the view shows a row header: the row numbers, or the column names of a transposed view. */
  private boolean showsRowHeader() { return myShowRowNumbers || isTransposed(); }

  /** A header listens to the data model it was made for, so a transpose, which replaces that model, needs a new one. */
  private void replaceRowHeaderForNewDataModel() {
    setRowHeader(showsRowHeader() ? createRowHeader() : null);
  }

  /** Gives the controller {@code header} to show, or none, and disposes the header it replaces. */
  private void setRowHeader(@Nullable GridRowHeader header) {
    GridRowHeader replaced = myRowHeader;
    myRowHeader = header;
    myFrozenColumnsController.showRowHeader(header);
    if (replaced != null && replaced != header) Disposer.dispose(replaced);
  }

  /** The row numbers take the width their highest number needs, beside the pinned table too. */
  private void resizeRowHeader() {
    if (myRowHeader == null) return;
    myRowHeader.updatePreferredSize();
    myRowHeader.revalidate();
    myRowHeader.repaint();
  }

  /** A row header with its width computed now: one made after the rows load misses the data event that sizes it, and stays 1px wide. */
  private @NotNull GridRowHeader createRowHeader() {
    GridRowHeader header = myResultPanel.createRowHeader(this);
    // Disposed when replaced, and with the view at the latest; one made after the view is gone follows nothing.
    if (!Disposer.tryRegister(this, header)) Disposer.dispose(header);
    header.updatePreferredSize();
    return header;
  }

  @Override
  public void dispose() {
    myDisposed = true;
    setStatisticsHeader(null);
    Disposer.dispose(myFrozenColumnsController);
    dropPinnedTable();
    Disposer.dispose(myFloatingToolbar);
    Disposer.dispose(myColumns);
    Disposer.dispose(myPrimary);
    myPrimaryColumns.dispose();
    myGridSelectionEvents.dispose();
    myEditedValue.setValue(null);
  }

  /** The width the columns of both tables share: the scroll pane's width without the row numbers and the vertical scroll bar. */
  int getAvailableColumnsWidth() { return myFrozenColumnsController.getAvailableColumnsWidth(); }

  @Override
  public @NotNull ModelIndex<GridColumn> getContextColumn() { return myContextColumn; }

  @TestOnly
  public void setContextColumn(@NotNull ModelIndex<GridColumn> column) { myContextColumn = column; }

  /** The main table, which shows the unpinned columns. */
  @TestOnly
  @NotNull GridTable getPrimaryTable() { return myPrimary; }

  @TestOnly
  GridTable getPinnedTable() { return getFrozenView(); }

  @Override
  public void setShowHorizontalLines(boolean v) {
    forEachTable(table -> table.setShowHorizontalLines(v));
  }

  @Override
  public void setShowVerticalLines(boolean showVerticalLines) {
    forEachTable(table -> table.setShowVerticalLines(showVerticalLines));
  }

  @Override
  public void setAdditionalRowsCount(int additionalRowsCount) {
    forEachTable(table -> table.setAdditionalRowsCount(additionalRowsCount));
  }

  @Override
  public void setVisibleRowCount(int visibleRowCount) {
    forEachTable(table -> table.setVisibleRowCount(visibleRowCount));
  }

  @Override
  public void setStriped(boolean striped) {
    forEachTable(table -> table.setStriped(striped));
  }

  @Override
  public void setTransparentColumnHeaderBackground(boolean v) {
    forEachTable(table -> table.setTransparentColumnHeaderBackground(v));
  }

  @Override
  public void setAllowMultilineLabel(boolean v) {
    forEachTable(table -> table.setAllowMultilineLabel(v));
  }

  @Override
  public void setValueAt(@Nullable Object v,
                         @NotNull ModelIndex<GridRow> row,
                         @NotNull ModelIndex<GridColumn> column,
                         boolean allowImmediateUpdate,
                         @NotNull GridRequestSource source) {
    setValueAt(v, row, column, allowImmediateUpdate, source, null);
  }

  @Override
  public void setValueAt(@Nullable Object v,
                         @NotNull ModelIndex<GridRow> row,
                         @NotNull ModelIndex<GridColumn> column,
                         boolean allowImmediateUpdate,
                         @NotNull GridRequestSource source,
                         @Nullable Object metadata) {
    writeValue(v, row, column, allowImmediateUpdate, source, metadata, null);
  }

  /** Writes a value; after a write committed by {@code committingEditor}, the selection moves on unless the editor says otherwise. */
  private void writeValue(@Nullable Object v,
                          @NotNull ModelIndex<GridRow> row,
                          @NotNull ModelIndex<GridColumn> column,
                          boolean allowImmediateUpdate,
                          @NotNull GridRequestSource source,
                          @Nullable Object metadata,
                          @Nullable GridTableCellEditor committingEditor) {
    SelectionModel<GridRow, GridColumn> selection = myResultPanel.getSelectionModel();
    boolean multi = isMultiEditingAllowed();
    ModelIndexSet<GridRow> rows = multi ? selection.getSelectedRows() : ModelIndexSet.forRows(myResultPanel, row.asInteger());
    ModelIndexSet<GridColumn> columns =
      multi ? selection.getSelectedColumns() : ModelIndexSet.forColumns(myResultPanel, column.asInteger());
    ViewIndex<GridRow> lastRow = rows.toView(myResultPanel).last();
    ViewIndex<GridColumn> lastColumn = columns.toView(myResultPanel).last();
    Runnable moveToNextCell =
      committingEditor != null && committingEditor.shouldMoveFocus() ? () -> moveToNextCellLater(lastRow, lastColumn) : null;
    myResultPanel.setValueAt(rows, columns, v, allowImmediateUpdate, moveToNextCell, source, metadata);
  }

  private void moveToNextCellLater(@NotNull ViewIndex<GridRow> rowIndex, @NotNull ViewIndex<GridColumn> colIndex) {
    IdeFocusManager.findInstanceByComponent(myScrollPane).doWhenFocusSettlesDown(
      // doWhenFocusSettlesDown causes "Write-unsafe context" so we need to call invokeLater
      () -> ApplicationManager.getApplication().invokeLater(() -> moveToNextCell(rowIndex, colIndex)));
  }

  private void moveToNextCell(@NotNull ViewIndex<GridRow> rowIndex, @NotNull ViewIndex<GridColumn> colIndex) {
    SelectionModel<GridRow, GridColumn> selectionModel = myResultPanel.getSelectionModel();
    if (selectionModel.getSelectedRowCount() != 1 || selectionModel.getSelectedColumnCount() != 1 ||
        !ApplicationManager.getApplication().isUnitTestMode() && (!UIUtil.isFocusAncestor(myScrollPane) || isEditing())) {
      return;
    }

    // if selection has already been changed by Tab or Shift-Tab we don't need to change it
    if (!Comparing.equal(selectionModel.getSelectedRow(), rowIndex.toModel(myResultPanel)) ||
        !Comparing.equal(selectionModel.getSelectedColumn(), colIndex.toModel(myResultPanel))) {
      return;
    }

    if (GridUtil.isInsertedRow(myResultPanel, rowIndex.toModel(myResultPanel))) {
      ViewIndex<GridColumn> nextColumn = ViewIndex.forColumn(myResultPanel, colIndex.asInteger() + 1);
      colIndex = nextColumn.isValid(myResultPanel) ? nextColumn : colIndex;
    }
    else {
      ViewIndex<GridRow> nextRow = ViewIndex.forRow(myResultPanel, rowIndex.asInteger() + 1);
      rowIndex = nextRow.isValid(myResultPanel) ? nextRow : rowIndex;
    }

    GridUtil.scrollToLocally(myResultPanel, rowIndex, colIndex);
  }

  @Override
  public void setTransposed(boolean transposed) {
    myWasAutomaticallyTransposed = false;
    transpose(transposed);
  }

  private void transpose(boolean transposed) {
    if (isTransposed() == transposed) return;
    // A transposed grid has no pinned columns by design: drop them first; the grid pins them again when its layout is refreshed.
    if (transposed) setFrozenColumns(Set.of());
    // The transpose replaces the data model, which a pinned table would go on showing.
    LOG.assertTrue(getFrozenView() == null, "A transposed grid has no pinned table");
    myDataModel = transposed ? new TransposedGridTableModel(myResultPanel) : new RegularGridTableModel(myResultPanel);
    GridUtil.saveAndRestoreSelection(myResultPanel, () -> {
      myColumns.startTranspose(transposed, myDataModel);
      myPrimary.doTranspose(myDataModel);
      // A header for the new model, before the grid pins columns again and the pinned table gets the row numbers beside it.
      replaceRowHeaderForNewDataModel();
      myColumns.createDefaultColumnsFromModel();
      if (!transposed) {
        // The grid applies its column order, pins and widths again; then the untransposed widths come back.
        if (myResultPanel instanceof ColumnOrderRestorer restorer) restorer.refreshColumnLayout();
        myColumns.restoreUntransposedColumnWidths();
      }
    });
  }

  @Override
  public @Nullable JScrollBar getVerticalScrollBar() {
    return myPrimary.getVerticalScrollBar();
  }

  @Override
  public @Nullable JScrollBar getHorizontalScrollBar() {
    return myPrimary.getHorizontalScrollBar();
  }

  @Override
  public void resetLayout() {
    myColumnLayout.resetLayout();
  }

  @Override
  public void growSelection() {
    myGrower.growSelection();
  }

  @Override
  public void shrinkSelection() {
    myGrower.shrinkSelection();
  }

  /** The cell at the top left of the visible area, in the grid's view indices: in the pinned strip while columns are pinned. */
  @NotNull Pair<ViewIndex<GridRow>, ViewIndex<GridColumn>> firstVisibleCell() {
    Point position = myScrollPane.getViewport().getViewPosition();
    int row = indexOrZero(myPrimary.rowAtPoint(position), myPrimary.getRowCount());
    int column = indexOrZero(masterColumn(myPrimary, myPrimary.columnAtPoint(position)), myColumns.getColumnModel().getColumnCount());
    GridTable frozen = getFrozenView();
    boolean transposed = isTransposed();
    if (frozen != null && frozen.getColumnCount() > 0 && !transposed) {
      // The strip scrolls sideways on its own when it overflows, so take its column at the left edge of its viewport.
      JViewport strip = ObjectUtils.tryCast(frozen.getParent(), JViewport.class);
      int stripX = strip == null ? 0 : strip.getViewPosition().x;
      int stripColumn = indexOrZero(frozen.columnAtPoint(new Point(stripX, 0)), frozen.getColumnCount());
      int pinnedColumn = masterColumn(frozen, stripColumn);
      if (pinnedColumn >= 0) column = pinnedColumn;
    }
    return new Pair<>(ViewIndex.forRow(myResultPanel, transposed ? column : row),
                      ViewIndex.forColumn(myResultPanel, transposed ? row : column));
  }

  private void updateCellImageCacheSize() {
    int[] size = {0};
    forEachTable(table -> size[0] += table.requestedCellImageCacheSize());
    myCellImageCache.adjustCacheSize(size[0]);
  }

  private static int indexOrZero(int index, int count) {
    return index < 0 || index >= count ? 0 : index;
  }

  @Override
  public void addSelectionChangedListener(@NotNull Consumer<Boolean> listener) {
    myPrimary.addSelectionChangedListener(listener, myGridSelectionEvents.getSelectionModel());
  }

  @Override
  public void restoreColumnsOrder(Map<Integer, ModelIndex<GridColumn>> expectedToModel) {
    // The order lives in the view's columns, which both tables move.
    myColumns.restoreColumnsOrder(expectedToModel);
  }

  @NotNull ModelIndex<GridColumn> uiColumn(int uiColumn) {
    return myPrimary.uiColumn(uiColumn);
  }

  int fromRealRowIdx(int uiRow) {
    return myPrimary.fromRealRowIdx(uiRow);
  }

  @Override
  public void showFirstCell(int rowNumOnCurrentPage) {
    myPrimary.showFirstCell(rowNumOnCurrentPage);
  }

  @Override
  public void searchSessionUpdated() {
    updateRowFilter();
    myScrollPane.repaint();
  }

  @TestOnly
  public @Nullable JComponent getCellRendererComponent(@NotNull ViewIndex<GridRow> viewRow,
                                                       @NotNull ViewIndex<GridColumn> viewColumn,
                                                       boolean forDisplay) {
    return cellRendererComponent(viewRow, viewColumn, forDisplay);
  }

  /** Renders the cell in the table that shows its column. */
  private @Nullable JComponent cellRendererComponent(@NotNull ViewIndex<GridRow> viewRow,
                                                     @NotNull ViewIndex<GridColumn> viewColumn,
                                                     boolean forDisplay) {
    if (!viewRow.isValid(myResultPanel) || !viewColumn.isValid(myResultPanel)) return null;
    boolean transposed = isTransposed();
    int row = (transposed ? viewColumn : viewRow).asInteger();
    int column = (transposed ? viewRow : viewColumn).asInteger();
    GridTable table = tableOf(column);
    int localColumn = localColumn(table, column);
    return localColumn < 0 ? null : table.getCellRendererComponent(row, localColumn, forDisplay);
  }

  @Override
  public void setColumnEnabled(@NotNull ModelIndex<GridColumn> columnIdx, boolean state) {
    // The table that shows the column gains or loses it through its slice.
    myColumns.setColumnEnabled(columnIdx, state);
    updatePinnedTable();
  }

  @Override
  public void setColumnsEnabled(@NotNull List<ModelIndex<GridColumn>> columns, boolean state) {
    myColumns.setColumnsEnabled(columns, state);
    updatePinnedTable();
  }

  @Override
  public void setRowEnabled(@NotNull ModelIndex<GridRow> rowIdx, boolean state) {
    myColumns.setRowEnabled(rowIdx, state);
  }

  void startPaintingSession() {
    forEachTable(table -> table.startPaintingSession());
  }

  void endPaintingSession() {
    forEachTable(table -> table.endPaintingSession());
  }

  @Override
  public void updateSortKeysFromColumnAttributes() {
    myRowOrder.updateSortKeys();
  }

  @Override
  public void orderingAndVisibilityChanged() {
    myPrimary.orderingAndVisibilityChanged();
  }

  @Override
  public @NotNull RawIndexConverter getRawIndexConverter() {
    return myRawIndexConverter;
  }

  @Override
  public void addMouseListenerToComponents(@NotNull MouseListener listener) {
    myPrimary.addMouseListenerToComponents(listener);
    myComponentMouseListeners.add(listener);
    GridTable pinned = myTables.getPinned();
    if (pinned != null) pinned.addMouseListener(listener);
  }

  @Override
  public boolean supportsCustomSearchSession() {
    return myPrimary.supportsCustomSearchSession();
  }

  @Override
  public @NotNull DataGridSearchSession createSearchSession(@Nullable FindModel findModel, @Nullable Component previousFilterComponent) {
    return myPrimary.createSearchSession(findModel, previousFilterComponent);
  }

  @Override
  public void searchSessionStarted(@NotNull SearchSession searchSession) {
    if (!(searchSession instanceof GridSearchSession<?, ?> session)) return;
    session.addListener(this, this);
  }

  /** Takes a point in the main table's coordinates; the tables share their rows, so the row is the same in both. */
  public int rowAtPoint(@NotNull Point point) {
    return myPrimary.rowAtPoint(point);
  }

  @Override
  public boolean isViewModified() {
    for (int viewColumnIdx = 0; viewColumnIdx < myColumns.getColumnModel().getColumnCount(); viewColumnIdx++) {
      ViewIndex<?> viewIndex =
        isTransposed() ? ViewIndex.forRow(myResultPanel, viewColumnIdx) : ViewIndex.forColumn(myResultPanel, viewColumnIdx);
      ModelIndex<?> modelIndex = viewIndex.toModel(myResultPanel);
      if (modelIndex.isValid(myResultPanel) && viewIndex.isValid(myResultPanel) && modelIndex.asInteger() != viewIndex.asInteger()) {
        return true;
      }
    }

    for (TableResultViewColumn tableColumn : myColumns.getColumnCache()) {
      if (!tableColumn.isWidthSetByLayout()) {
        return true;
      }
    }

    int defaultRowHeight = getRowHeight();
    for (int i = 0; i < getRowCount(); i++) {
      if (getRowHeight(i) != defaultRowHeight) {
        return true;
      }
    }

    return false;
  }

  @Override
  public void contentLanguageUpdated(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull Language language) {
    forEachTable(table -> table.clearCache(columnIdx));
  }

  @Override
  public void displayTypeUpdated(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull DisplayType displayType) {
    forEachTable(table -> table.clearCache(columnIdx));
  }

  /** Takes a row of the main table; both tables show the same rows. */
  void setRowHeight(int row, int rowHeight) {
    if (row < 0 || row >= myPrimary.getRowCount()) return;
    myRowHeights.setHeight(myPrimary.convertRowIndexToModel(row), rowHeight);
  }

  /** The height that fits every cell of {@code row} in every table, which share their rows, and at least the default row height. */
  int expandedRowHeight(int row) {
    int height = myPrimary.getRowHeight();
    for (GridTable table : tables()) {
      for (int column = 0; column < table.getColumnCount(); column++) {
        TableCellRenderer renderer = table.getCellRenderer(row, column);
        if (renderer != null) height = Math.max(height, table.prepareRenderer(renderer, row, column).getPreferredSize().height);
      }
    }
    return height;
  }

  /** Ends a column drag in the header of every table, before the column the drag holds is removed. */
  void clearDraggedColumns() {
    forEachTable(table -> table.getTableHeader().setDraggedColumn(null));
  }

  @Override
  public void columnsAdded(ModelIndexSet<GridColumn> columnIndices) {
    // The pinned table gets the model event from the shared data model, and the new columns through its slice.
    myColumns.columnAttributesUpdated();
    myPrimary.columnsAdded(columnIndices);
    updatePinnedTable();
  }

  @Override
  public void columnAttributesUpdated() {
    myColumns.columnAttributesUpdated();
    updatePinnedTable();
  }

  @Override
  public void columnsRemoved(ModelIndexSet<GridColumn> columns) {
    myPrimary.columnsRemoved(columns);
    if (!isTransposed()) myColumns.createDefaultColumnsFromModel();
    myCellImageCache.reset();
    updatePinnedTable();
  }

  @Override
  public void rowsAdded(ModelIndexSet<GridRow> rows) {
    // The pinned table gets the model event from the shared data model.
    myPrimary.rowsAdded(rows);
    if (isTransposed()) myColumns.createDefaultColumnsFromModel();
    myColumnLayout.newRowsAdded(rows);
  }

  @Override
  public void afterLastRowAdded() {
    DataGridSettings settings = GridUtil.getSettings(myResultPanel);
    if (!isTransposed() && getViewRowCount() == 1 &&
        settings != null && settings.getAutoTransposeMode() == AutoTransposeMode.ONE_ROW) {
      transpose(true);
      myWasAutomaticallyTransposed = true;
      return;
    }
    if (myWasAutomaticallyTransposed && isTransposed() && getViewRowCount() != 1) {
      setTransposed(false);
      return;
    }
    myPrimary.afterLastRowAdded();
    // Every row is loaded, so the row numbers take their final width.
    resizeRowHeader();
  }

  @Override
  public void addSpaceForHorizontalScrollbar(boolean v) {
    myPrimary.addSpaceForHorizontalScrollbar(v);
  }

  @Override
  public void expandMultilineRows(boolean v) {
    myPrimary.expandMultilineRows(v);
  }

  @Override
  public void rowsRemoved(ModelIndexSet<GridRow> rows) {
    myPrimary.rowsRemoved(rows);
    if (isTransposed()) myColumns.createDefaultColumnsFromModel();
  }

  @Override
  public void cellsUpdated(ModelIndexSet<GridRow> rows, ModelIndexSet<GridColumn> columns, @Nullable GridRequestSource.RequestPlace place) {
    myPrimary.cellsUpdated(rows, columns, place);
  }

  @Override
  public void changeSelectedColumnsWidth(int delta) {
    GridTable.MyTableColumnModel columns = myColumns.getColumnModel();
    int[] selectedColumns =
      Arrays.stream(columns.getSelectedColumns()).filter(column -> column >= 0 && column < columns.getColumnCount()).toArray();
    int pinnedCount = 0;
    for (int column : selectedColumns) {
      if (isPinnedColumn(column)) pinnedCount++;
    }
    int constrainedDelta =
      myFrozenColumnsController.constrainSelectedColumnWidthDelta(pinnedCount, selectedColumns.length - pinnedCount, delta);
    for (int column : selectedColumns) {
      TableResultViewColumn tableColumn = columns.getColumn(column);
      tableColumn.setColumnWidthByUser(Math.max(0, tableColumn.getColumnWidth() + constrainedDelta));
    }
  }

  @Override
  public void fitColumnsToViewport() {
    List<TableResultViewColumn> columns = myColumns.visibleColumns();
    int width = getAvailableColumnsWidth();
    if (columns.isEmpty() || width <= 0) return;
    int columnWidth = Math.max(JBUI.scale(40), width / columns.size());
    for (TableResultViewColumn column : columns) column.setColumnWidthByUser(columnWidth);
  }

  @Override
  public void scrollColumnsIntoView(@NotNull List<ModelIndex<GridColumn>> columns) {
    myPrimary.scrollColumnsIntoView(columns);
  }

  /** Returns the unpinned columns that fit in the pinned strip. */
  @Override
  public @NotNull Set<ModelIndex<GridColumn>> columnsThatCanBePinned(@NotNull Set<ModelIndex<GridColumn>> pinnedColumns) {
    var candidates = new ArrayList<TableResultViewColumn>();
    int pinnedWidth = 0;
    int unpinnedWidth = 0;
    for (TableResultViewColumn column : myColumns.visibleColumns()) {
      int width = column.getFrozenStripWidth();
      if (pinnedColumns.contains(modelIndexOf(column))) {
        pinnedWidth += width;
      }
      else {
        unpinnedWidth += width;
        candidates.add(column);
      }
    }
    Set<ModelIndex<GridColumn>> result = new HashSet<>();
    int availableWidth = getAvailableColumnsWidth();
    for (var column : candidates) {
      int width = column.getFrozenStripWidth();
      if (availableWidth <= 0 || PinnedColumnsFit.fits(pinnedWidth + width, unpinnedWidth - width, availableWidth)) {
        result.add(modelIndexOf(column));
      }
    }
    return result;
  }

  private @NotNull ModelIndex<GridColumn> modelIndexOf(@NotNull TableResultViewColumn column) {
    return ModelIndex.forColumn(myResultPanel, column.getModelIndex());
  }

  /** Checks whether the visible pinned columns leave enough width for the unpinned columns. */
  @Override
  public boolean canFitPinnedColumns(@NotNull Set<ModelIndex<GridColumn>> pinnedColumns) {
    int availableWidth = getAvailableColumnsWidth();
    if (availableWidth <= 0) return true;
    int pinnedWidth = 0;
    int unpinnedWidth = 0;
    for (TableResultViewColumn column : myColumns.visibleColumns()) {
      int width = column.getFrozenStripWidth();
      if (pinnedColumns.contains(modelIndexOf(column))) pinnedWidth += width;
      else unpinnedWidth += width;
    }
    return PinnedColumnsFit.fits(pinnedWidth, unpinnedWidth, availableWidth);
  }

  @Override
  public void resetColumnWidths() {
    myColumns.resetColumnWidths();
  }

  @Override
  public void resetRowHeights() {
    myRowHeights.clear();
  }

  public @NotNull LocalFilterState getLocalFilterState() {
    return myPrimary.getLocalFilterState();
  }

  public void updateRowFilter() {
    myRowOrder.updateRowFilter();
  }

  @Override
  public @Nullable Color getCellBackground(@NotNull ViewIndex<GridRow> row, @NotNull ViewIndex<GridColumn> column, boolean selected) {
    return myPrimary.getCellBackground(row, column.toModel(myResultPanel), selected);
  }

  @Override
  public @NotNull Color getCellForeground(boolean selected) {
    return myPrimary.getCellForeground(selected);
  }

  @Override
  public void reinitSettings() {
    forEachTable(table -> table.reinitSettings());
  }

  /** Takes view indices of the main table. */
  @TestOnly
  public boolean editCellAt(int row, int column, EventObject e) {
    return myPrimary.editCellAt(row, column, e);
  }

  @Override
  public void createDefaultColumnsFromModel() {
    myColumns.createDefaultColumnsFromModel();
    updatePinnedTable();
  }

  public Font getFont() {
    return myPrimary.getFont();
  }

  @Override
  public void globalSchemeChange(@Nullable EditorColorsScheme scheme) {
    forEachTable(table -> table.globalSchemeChange(scheme));
  }

  void changeFontSize(int increment, double scale) {
    forEachTable(table -> table.changeFontSize(increment, scale));
  }

  @Override
  public void uiSettingsChanged(@NotNull UISettings uiSettings) {
    forEachTable(table -> table.uiSettingsChanged(uiSettings));
  }

  @Override
  public int getRowHeight() {
    return myPrimary.getRowHeight();
  }

  public Color getBackground() {
    return myPrimary.getBackground();
  }

  public Color getGridColor() {
    return myPrimary.getGridColor();
  }

  public GridTableModel getModel() {
    return myDataModel;
  }

  @Override
  public boolean isTransposed() {
    return myPrimary.isTransposed();
  }

  @Override
  public void onColumnHierarchyChanged() {
    myPrimary.onColumnHierarchyChanged();
  }

  @Override
  public @Nullable ResultViewColumn getLayoutColumn(@NotNull ModelIndex<?> column) {
    return myColumns.getLayoutColumn(column);
  }

  /** The same column as {@link #getLayoutColumn}: the table that shows a column shows the view's own column object. */
  @Override
  public @Nullable ResultViewColumn getColumnForPersistence(@NotNull ModelIndex<?> column) {
    return myColumns.getLayoutColumn(column);
  }

  @Override
  public @Nullable ResultViewColumn getLayoutColumnForDataColumn(@NotNull ModelIndex<GridColumn> column) {
    // Layout columns of a transposed view are backed by rows, so a data column has no layout column of its own.
    return isTransposed() ? null : myColumns.getLayoutColumn(column);
  }

  @Override
  public @NotNull ModelIndexSet<GridRow> getVisibleRows() {
    return myPrimary.getVisibleRows();
  }

  @Override
  public @NotNull ModelIndexSet<GridColumn> getVisibleColumns() {
    int visibleColumns = getViewColumnCount();
    int[] viewIndices = new int[visibleColumns];
    for (int i = 0; i < visibleColumns; i++) {
      viewIndices[i] = i;
    }
    ModelIndexSet<GridColumn> columnIndices = ViewIndexSet.forColumns(myResultPanel, viewIndices).toModel(myResultPanel);
    IntList validIndices = new IntArrayList(columnIndices.size());
    for (ModelIndex<GridColumn> idx : columnIndices.asIterable()) {
      if (idx.isValid(myResultPanel)) {
        validIndices.add(idx.asInteger());
      }
    }
    return ModelIndexSet.forColumns(myResultPanel, validIndices.toIntArray());
  }

  @Override
  public int getViewColumnCount() {
    return isTransposed() ? getRowCount() : myColumns.getColumnModel().getColumnCount();
  }

  @Override
  public int getViewRowCount() {
    return myPrimary.getViewRowCount();
  }

  @Override
  public boolean stopEditing() {
    GridTable editing = editingTable();
    return editing == null || editing.stopEditing();
  }

  @Override
  public void cancelEditing() {
    forEachTable(GridTable::cancelEditing);
  }

  @Override
  public boolean isCellEditingAllowed() {
    return true;
  }

  /** Gives the focus to the table that shows the lead column when the other table of the view has it. */
  private void moveFocusToLeadColumnTable() {
    if (myColumns.getColumnModel().getSelectionModel().getLeadSelectionIndex() < 0 || isEditing() || getFrozenView() == null) return;
    IdeFocusManager focusManager = IdeFocusManager.getInstance(myResultPanel.getProject());
    Component owner = focusManager.getFocusOwner();
    GridTable leadTable = leadColumnTable();
    if (owner != null && owner != leadTable && tables().contains(owner)) focusManager.requestFocus(leadTable, false);
  }

  /** Points at the lead cell in the table that shows it, which may be the pinned table. */
  @Override
  public @NotNull RelativePoint getBestPopupLocation() {
    return JBPopupFactory.getInstance().guessBestPopupLocation(leadColumnTable());
  }

  /** The table that shows the lead column, or the main table while there is no lead. */
  @NotNull GridTable leadColumnTable() {
    int column = myColumns.getColumnModel().getSelectionModel().getLeadSelectionIndex();
    return column < 0 ? myPrimary : tableOf(column);
  }

  @Override
  public void editSelectedCell() {
    int row = myRowSelection.getLeadSelectionIndex();
    int column = myColumns.getColumnModel().getSelectionModel().getLeadSelectionIndex();
    if (row < 0 || column < 0) return;
    GridTable target = tableOf(column);
    int targetColumn = localColumn(target, column);
    if (targetColumn >= 0) TableUtil.editCellAt(target, row, targetColumn);
  }

  @Override
  public void editSelectedCellWithValue(Object value) {
    editSelectedCellWithValue(value, true);
  }

  public void editSelectedCellWithValue(Object value, boolean shouldMoveFocus) {
    int column = myColumns.getColumnModel().getSelectionModel().getLeadSelectionIndex();
    if (column < 0) return;
    tableOf(column).withCurrentValue(value, shouldMoveFocus, this::editSelectedCell);
  }

  @Override
  public boolean isMultiEditingAllowed() {
    // The tables share the selection; only the table that holds the editor knows whether it may write a unique column.
    GridTable editing = editingTable();
    return (editing != null ? editing : myPrimary).isMultiEditingAllowed();
  }

  /** Takes view indices of the main table. */
  @TestOnly
  public void setValueAt(Object value, int viewRowIdx, int viewColumnIdx) {
    myPrimary.setValueAt(value, viewRowIdx, viewColumnIdx);
  }

  @Override
  public void resetScroll() {
    myPrimary.resetScroll();
  }

  @Override
  public boolean isHoveredRowBgHighlightingEnabled() {
    return myPrimary.isHoveredRowBgHighlightingEnabled();
  }

  @Override
  public void setHoveredRowHighlightMode(HoveredRowBgHighlightMode mode) {
    forEachTable(table -> table.setHoveredRowHighlightMode(mode));
  }

  public void setStatisticsHeader(StatisticsTableHeader statisticsHeader) {
    if (myStatisticsHeaderHost != null) myStatisticsHeaderHost.installTable(null);
    if (myStatisticsHeader != null) myStatisticsHeader.installView(null);
    myStatisticsHeader = statisticsHeader;
    if (statisticsHeader == null) {
      myStatisticsHeaderHost = null;
    }
    else {
      myStatisticsHeaderHost = new StatisticsHeaderHost(myPrimary, this, statisticsHeader);
    }
  }

  public StatisticsTableHeader getStatisticsHeader() {
    return myStatisticsHeader;
  }

  public void setStatisticsPanelMode(StatisticsPanelMode newPanelMode) {
    StatisticsPanelMode previousPanelMode = getStatisticsPanelMode();
    if (previousPanelMode == newPanelMode || myStatisticsHeader == null) return;
    myStatisticsHeader.setStatisticsPanelMode(newPanelMode);
    if (previousPanelMode != null) myColumnLayout.resetLayout();
  }

  public StatisticsPanelMode getStatisticsPanelMode() {
    return myStatisticsHeader == null ? null : myStatisticsHeader.getStatisticsPanelMode();
  }

  @Override
  public void onLocalFilterStateChanged() {
    updateRowFilter();
  }

  /** The table whose rows the row-number gutter follows, for row geometry, paint settings and the Swing row selection. */
  @NotNull JBTable rowHeaderTable() { return myPrimary; }

  /** The view's column model, which holds the grid's columns in grid order, so the grid's column indices are its indices. */
  @NotNull TableColumnModel getGridColumnModel() { return myColumns.getColumnModel(); }

  void toggleSortOrder(@NotNull ModelIndex<GridColumn> column, boolean additive) {
    myResultPanel.getAutoscrollLocker().runWithLock(() -> myResultPanel.toggleSortColumns(Collections.singletonList(column), additive));
  }

  void invokeColumnPopup(@NotNull ModelIndex<GridColumn> column, @NotNull Component component, @NotNull Point point) {
    myPrimary.invokeColumnPopup(column, component, point);
  }

  void invokeRowPopup(@NotNull Component component, int x, int y) { myPrimary.invokeRowPopup(component, x, y); }

  int getTextLineHeight() { return myPrimary.getTextLineHeight(); }

  /*
   * The helpers below take positions in the view's column model, as Swing gives them, and ask the view's tables, which know which
   * table shows which column. Untransposed, a position is a grid column's view index; transposed, the columns are the grid's rows,
   * and nothing is pinned.
   */

  private @NotNull GridTable tableOf(int position) { return myTables.tableAt(position); }

  private boolean isPinnedColumn(int position) { return tableOf(position) != myPrimary; }

  /** The index in {@code table} of the column at {@code position}, or -1 when the table doesn't show it. */
  private int localColumn(@NotNull GridTable table, int position) { return myTables.localIndex(table, position); }

  /** The position in the view's column model of the column at {@code localIndex} of {@code table}, or -1. */
  private int masterColumn(@NotNull GridTable table, int localIndex) { return myTables.position(table, localIndex); }

  /** The index of model column {@code modelColumn} in the view's column model, or -1. */
  private int gridColumnOfModel(int modelColumn) {
    return myRawIndexConverter.columnIndexToView(modelColumn);
  }

  private void selectHeaderColumn(GridTable source, int column, MouseEvent event) {
    boolean interval = GridUtil.isIntervalModifierSet(event);
    boolean exclusive = GridUtil.isExclusiveModifierSet(event);
    TableSelectionModel selection = ObjectUtils.tryCast(myPrimary.gridSelectionModel(), TableSelectionModel.class);
    if (selection == null) return;
    if (interval && myFrozenColumnsController.selectDisplayedColumnRange(source, column, exclusive)) {
      if (exclusive) {
        selection.addRowSelectionInterval(myPrimary.getRowCount() - 1, 0);
      }
      else {
        selection.setRowSelectionInterval(myPrimary.getRowCount() - 1, 0);
      }
      return;
    }
    int gridColumn = masterColumn(source, column);
    if (gridColumn < 0) return;
    ListSelectionModel columns = myColumns.getColumnModel().getSelectionModel();
    if (interval) {
      int lead = columns.getLeadSelectionIndex();
      if (lead < 0 || lead >= myColumns.getColumnModel().getColumnCount()) lead = gridColumn;
      if (exclusive) {
        selection.addRowSelectionInterval(myPrimary.getRowCount() - 1, 0);
        selection.addColumnSelectionInterval(gridColumn, lead);
      }
      else {
        selection.setRowSelectionInterval(myPrimary.getRowCount() - 1, 0);
        selection.setColumnSelectionInterval(gridColumn, lead);
      }
    }
    else if (exclusive) {
      if (columns.isSelectedIndex(gridColumn)) {
        columns.removeSelectionInterval(gridColumn, gridColumn);
      }
      else {
        selection.addRowSelectionInterval(myPrimary.getRowCount() - 1, 0);
        selection.addColumnSelectionInterval(gridColumn, gridColumn);
      }
    }
    else {
      selection.setRowSelectionInterval(myPrimary.getRowCount() - 1, 0);
      selection.setColumnSelectionInterval(gridColumn, gridColumn);
    }
  }

  private final class TableHost implements GridTableHost {
    private final GridTable myTable;

    private TableHost(GridTable table) { myTable = table; }

    @Override
    public boolean stopEditingBeforeSelectionChange(int row, int localColumn) {
      return myFrozenColumnsController.stopEditingBeforeSelectionChange(myTable, row, localColumn);
    }

    @Override
    public boolean handleCellRangeSelection(int row, int localColumn, boolean addToSelection) {
      return myFrozenColumnsController.handleCellRangeSelection(myTable, row, localColumn, addToSelection);
    }

    @Override
    public boolean beforeMouseEvent(@NotNull MouseEvent event) {
      return myFrozenColumnsController.beforeMouseEvent(myTable, event);
    }

    @Override
    public void afterMouseEvent(@NotNull MouseEvent event) {
      myFrozenColumnsController.afterMouseEvent(myTable, event);
    }

    @Override
    public void processMouseMotionEvent(@NotNull MouseEvent event) {
      myFrozenColumnsController.processMouseMotionEvent(myTable, event);
    }

    @Override
    public void installColumnNavigationActions(@NotNull ActionMap actionMap) {
      myFrozenColumnsController.installColumnNavigationActions(myTable, actionMap);
    }

    @Override
    public int constrainColumnResize(@NotNull TableResultViewColumn column, int width) {
      return myFrozenColumnsController.constrainFrozenColumnResize(myTable, column, width);
    }

    @Override
    public void columnMarginChanged() { myFrozenColumnsController.columnMarginChanged(myTable); }

    @Override
    public void setRowHeight(int row, int height) {
      // A dropped pinned table can still restore an editor's row height late, but its rows no longer match the grid's.
      if (myTable != myPrimary && myTable != getFrozenView()) return;
      myRowHeights.setHeight(myTable.convertRowIndexToModel(row), height);
    }

    @Override
    public void changeFontSize(int increment, double scale) { TableResultView.this.changeFontSize(increment, scale); }

    @Override
    public void toggleSortOrder(@NotNull ModelIndex<GridColumn> column, boolean additive) {
      TableResultView.this.toggleSortOrder(column, additive);
    }

    @Override
    public void cellImageCacheSizeChanged() { updateCellImageCacheSize(); }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      // An open cell editor sits inside a table, and its own shortcuts must win over cell actions bound to F2 or Alt+Enter.
      sink.set(DatabaseDataKeys.DATA_GRID_ACTION_UI_STATE_KEY, GridActionUiState.capture(myResultPanel, !isEditing()));
    }

    @Override
    public void setValueAt(@Nullable Object value, @NotNull ModelIndex<GridRow> row, @NotNull ModelIndex<GridColumn> column,
                           boolean allowImmediateUpdate, @NotNull GridRequestSource source, @Nullable Object metadata) {
      // The table still holds its editor here: Swing writes the committed value before it removes the editor.
      GridTableCellEditor editor = ObjectUtils.tryCast(myTable.getCellEditor(), GridTableCellEditor.class);
      writeValue(value, row, column, allowImmediateUpdate, source, metadata, editor);
    }

    @Override
    public boolean stopEditingBeforeEdit() {
      for (GridTable other : tables()) {
        TableCellEditor editor = other == myTable ? null : other.getCellEditor();
        if (editor != null && !editor.stopCellEditing()) return false;
      }
      return true;
    }

    @Override
    public void setContextColumn(@NotNull ModelIndex<GridColumn> column) { myContextColumn = column; }

    @Override
    public boolean columnPopupClosed(@NotNull ModelIndex<GridColumn> column) {
      if (!myContextColumn.equals(column)) return false;
      myContextColumn = ModelIndex.forColumn(myResultPanel, -1);
      return true;
    }

    @Override
    public void selectHeaderColumn(int localColumn, @NotNull MouseEvent event) {
      TableResultView.this.selectHeaderColumn(myTable, localColumn, event);
    }
  }

  /** Passes the requests of the view's columns on to the grid, which knows a view's column order by the view, and to the layout. */
  private final class ColumnsHost implements GridColumnsHost {
    @Override
    public boolean applyColumnsDisplayOrder() {
      return myResultPanel instanceof ColumnOrderRestorer restorer && restorer.applyColumnsDisplayOrder(TableResultView.this);
    }

    @Override
    public void columnMoved(@NotNull ColumnMove move) {
      if (myResultPanel instanceof ColumnOrderRestorer restorer) restorer.columnMovedInView(TableResultView.this, move);
    }

    @Override
    public void columnsShown(@NotNull ModelIndexSet<?> columns) { myColumnLayout.columnsShown(columns); }

    @Override
    public void columnsRemoving() { clearDraggedColumns(); }
  }

  private final class TableColumnLayoutContext implements ColumnLayoutContext {
    @Override
    public boolean isTransposed() { return TableResultView.this.isTransposed(); }

    @Override
    public ResultViewColumn getLayoutColumn(@NotNull ModelIndex<?> column) { return getColumnForPersistence(column); }

    @Override
    public int getHeaderWidth(@NotNull ResultViewColumn column) {
      int gridColumn = gridColumnOfModel(column.getModelIndex());
      GridTable table = tableOf(gridColumn);
      TableCellRenderer renderer = table.getTableHeader().getDefaultRenderer();
      Component component = renderer instanceof GridTable.MyCellRenderer header
                            ? header.getHeaderCellRendererComponent(column.getModelIndex(), false)
                            : renderer.getTableCellRendererComponent(table, column.getHeaderValue(), false, false, -1,
                                                                     localColumn(table, gridColumn));
      CellRendererPane pane = new CellRendererPane();
      pane.add(component);
      component.validate();
      int width = component.getPreferredSize().width + column.getAdditionalWidth();
      pane.removeAll();
      return width;
    }

    @Override
    public int getCellPreferredWidth(@NotNull ViewIndex<GridRow> row, @NotNull ViewIndex<GridColumn> column) {
      JComponent component = cellRendererComponent(row, column, false);
      return component == null ? 0 : component.getPreferredSize().width;
    }

    @Override
    public StatisticsPanelMode getStatisticsPanelMode() { return TableResultView.this.getStatisticsPanelMode(); }
  }

  public void repaint() { myScrollPane.repaint(); }

  public void revalidate() { myScrollPane.revalidate(); }

  /** The component and the point the floating toolbar is requested at, or null while none is requested. */
  @TestOnly
  @Nullable Pair<JComponent, Point> getRequestedToolbarDisplay() {
    return myFloatingToolbar.getRequestedDisplay();
  }

  /** The data context the actions of the floating toolbar get. */
  @TestOnly
  @NotNull DataContext getToolbarActionDataContext() {
    return myFloatingToolbar.getActionDataContext();
  }

  public void setStatisticsHeaderPosition(AdditionalTableHeader.Position position) {
    if (myStatisticsHeaderHost != null) myStatisticsHeaderHost.setPosition(position);
  }

  public void setColumnHeaderPainter(@Nullable ColumnHeaderPainter painter) {
    forEachTable(table -> table.setColumnHeaderPainter(painter));
  }

  /** Only the table diff uses it, and its grid cannot pin columns. */
  public void addColumnHeaderMouseListener(@NotNull MouseListener listener) {
    myPrimary.getTableHeader().addMouseListener(listener);
  }

  public int getColumnHeaderHeight() { return myPrimary.getTableHeader().getHeight(); }

  /** The statistics header lies over the main table's columns only, so it follows a drag in that header. */
  public @Nullable TableColumn getDraggedColumn() { return myPrimary.getTableHeader().getDraggedColumn(); }

  public int getHeaderDraggedDistance() { return myPrimary.getTableHeader().getDraggedDistance(); }

  /** Refreshes the tables as for a new structure of the data, which also forgets the row order and the row heights. */
  public void refreshCells() {
    myRowOrder.structureChanged();
    forEachTable(table -> table.tableChanged(null));
  }

  /** Takes a point in the main table's coordinates and returns a main-table column index, which is not a grid index. */
  public int columnAtPoint(@NotNull Point point) { return myPrimary.columnAtPoint(point); }

  @Override
  public int getHoveredRow() {
    GridTable frozen = getFrozenView();
    int row = frozen == null ? -1 : TableHoverListener.getHoveredRow(frozen);
    return row >= 0 ? row : TableHoverListener.getHoveredRow(myPrimary);
  }

  @TestOnly
  public void removeEditor() { forEachTable(GridTable::removeEditor); }

  @Override
  public void setFitColumnsToWidth(boolean v) {
    // The pinned table keeps its own widths: the columns that fit are the ones the main table shows.
    myPrimary.setAutoResizeMode(v ? JTable.AUTO_RESIZE_ALL_COLUMNS : JTable.AUTO_RESIZE_OFF);
  }

  /** The columns the main table shows, which the statistics header lines up with. */
  public TableColumnModel getColumnModel() { return myPrimary.getColumnModel(); }

  public ComponentOrientation getComponentOrientation() { return myPrimary.getComponentOrientation(); }

  public int getWidth() { return myPrimary.getWidth(); }

  public int getRowCount() { return myPrimary.getRowCount(); }

  /** The columns the main table shows, without the pinned ones; {@link #getViewColumnCount} counts the grid's. */
  public int getColumnCount() { return myPrimary.getColumnCount(); }

  /** Takes grid indices. */
  @TestOnly
  public void moveColumn(int from, int to) { myColumns.getColumnModel().moveColumn(from, to); }

  public int getRowHeight(int row) { return myPrimary.getRowHeight(row); }

  @Override
  public boolean getShowHorizontalLines() { return myPrimary.getShowHorizontalLines(); }

  @Override
  public boolean isStriped() { return myPrimary.isStriped(); }

  /** Takes a main-table column index, as {@link #columnAtPoint} returns. */
  public Rectangle getCellRect(int row, int column, boolean includeSpacing) { return myPrimary.getCellRect(row, column, includeSpacing); }
}
