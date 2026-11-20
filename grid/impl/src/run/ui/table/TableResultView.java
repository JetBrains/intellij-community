package com.intellij.database.run.ui.table;

import com.intellij.database.DatabaseDataKeys;
import com.intellij.database.actions.ShowEditMaximizedAction;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.DataGridListener;
import com.intellij.database.datagrid.DataGridWithNestedTables;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.GridColumnLayout;
import com.intellij.database.datagrid.GridHelper;
import com.intellij.database.datagrid.GridModel;
import com.intellij.database.datagrid.GridRequestSource;
import com.intellij.database.datagrid.GridRow;
import com.intellij.database.datagrid.GridUtil;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.datagrid.ModelIndexSet;
import com.intellij.database.datagrid.RawIndexConverter;
import com.intellij.database.datagrid.ResultView;
import com.intellij.database.datagrid.ResultViewColumn;
import com.intellij.database.datagrid.ResultViewSelectionListener;
import com.intellij.database.datagrid.SelectionModel;
import com.intellij.database.datagrid.SelectionModelUtil;
import com.intellij.database.datagrid.ViewIndex;
import com.intellij.database.extractors.DisplayType;
import com.intellij.database.run.ui.ColumnMove;
import com.intellij.database.run.ui.ColumnOrderRestorer;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.database.run.ui.DataGridRequestPlace;
import com.intellij.database.run.ui.GridTableCellEditor;
import com.intellij.database.run.ui.ResultViewWithCells;
import com.intellij.database.run.ui.ResultViewWithColumns;
import com.intellij.database.run.ui.ResultViewWithFrozenColumns;
import com.intellij.database.run.ui.ResultViewWithRows;
import com.intellij.database.run.ui.TableAggregatorWidgetHelper;
import com.intellij.database.run.ui.ValueTabInfoProvider;
import com.intellij.database.run.ui.grid.ColumnLayoutContext;
import com.intellij.database.run.ui.grid.DataGridSearchSession;
import com.intellij.database.run.ui.grid.DummyGridColumnLayout;
import com.intellij.database.run.ui.grid.GridRowHeader;
import com.intellij.database.run.ui.grid.GridScrollPositionManager;
import com.intellij.database.run.ui.grid.GridSearchSession;
import com.intellij.database.run.ui.grid.TableCellImageCache;
import com.intellij.database.run.ui.grid.renderers.GridCellRendererFactories;
import com.intellij.database.run.ui.table.statisticsPanel.StatisticsPanelMode;
import com.intellij.database.run.ui.table.statisticsPanel.StatisticsTableHeader;
import com.intellij.database.settings.DataGridSettings;
import com.intellij.database.settings.DataGridSettings.AutoTransposeMode;
import com.intellij.database.util.GridActionUiState;
import com.intellij.find.FindModel;
import com.intellij.find.SearchSession;
import com.intellij.ide.DataManager;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataSink;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
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
import com.intellij.ui.ClientProperty;
import com.intellij.ui.TableUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.Magnificator;
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
import javax.swing.Action;
import javax.swing.CellRendererPane;
import javax.swing.DefaultListSelectionModel;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableColumnModel;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import java.awt.Color;
import java.awt.Container;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EventObject;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;

/**
 * The grid's table presentation: the main table and, while a visible column is pinned, the pinned table beside it.
 * <p>
 * The view owns the models both tables show: the data model, the view's column model, the row selection, the row order and the row heights.
 * It also owns the grid's selection model, scroll position manager, renderer adapters, header appearance and cell image cache. It hosts
 * the statistics header it is given. It keeps the value of an open multi-cell edit, which both tables show. It sets up every table the same
 * way, writes what cell editors commit, and gives grid actions their data. Each table shows its columns through a {@link GridColumnSlice}:
 * the main table the unpinned ones, the pinned table the pinned ones.
 * <p>
 * A call that reaches only the main table is still right in two cases. Either the pinned table follows the change through a shared model.
 * Or the call reads what both tables have alike: rows, row geometry or appearance. Any other main-only call says why it leaves out the
 * pinned table.
 * <p>
 * The view's component is the scroll pane that holds both tables, the row numbers and the headers. The preferred focused component is the
 * table that shows the lead cell. Ask for it at the moment of focus: the lead moves between the tables, and an unpin removes one.
 */
public final class TableResultView implements ResultView, ResultViewWithCells, ResultViewWithColumns, ResultViewWithRows,
                                              ResultViewWithFrozenColumns, EditorColorsListener, UISettingsListener {
  private static final Logger LOG = Logger.getInstance(TableResultView.class);

  private final DataGrid myGrid;
  /** The grid's data with its unsaved changes, which every data model of the view reads. */
  private final GridModel<GridRow, GridColumn> myGridModel;
  private final GridColumns myColumns;
  /** The grid's selection listeners, which hear of a change of the row or column selection once, after both tables took it. */
  private final GridSelectionEvents myGridSelectionEvents;
  /** The grid's selection model, which reads the view's models; the grid finds it on {@link #getComponent()}. */
  private final TableSelectionModel mySelectionModel;
  /** The view's columns in displayed order, which the slices, the header clicks and the controller share. */
  private final DisplayedColumns myDisplayedColumns;
  /** The main table's slice: the unpinned columns of the view's column model. */
  private final GridColumnSlice myMainSlice;
  /** The main table, the pinned table while a visible column is pinned, and the columns each shows. Only the view changes it. */
  private final MutableViewTables myTables;
  /** The header popups of both tables and of a transposed gutter, and the column the open column popup is about. */
  private final TableHeaderPopups myHeaderPopups;
  /** Decides what a click on a column header does; on the header of every table of the view, a pinned table created later too. */
  private final TableHeaderClicks myHeaderClicks;
  /** Which cells the view edits in the value editor instead of a cell editor, in every table of the view. */
  private final ValueEditorCells myValueEditorCells;
  /** Mouse listeners for every table of the view, which a pinned table created later gets too. */
  private final List<MouseListener> myCellMouseListeners = new ArrayList<>();
  private final List<MouseMotionListener> myCellMotionListeners = new ArrayList<>();
  private final List<FocusListener> myCellFocusListeners = new ArrayList<>();
  /** Applies a header drag in the pinned table to the grid's data, or null while there is no pinned table. */
  private @Nullable MoveColumnListener myPinnedMoveColumnListener;
  private final GridTable myMainTable;
  /** Converts the grid's view and model indices: columns through the view's column model, rows through the main table. */
  private final TableRawIndexConverter myRawIndexConverter;
  private final FrozenColumnsController myFrozenColumnsController;
  private final TableFloatingToolbarController myFloatingToolbar;
  private final TableScrollPane myScrollPane;
  private GridColumnLayout<GridRow, GridColumn> myColumnLayout = new DummyGridColumnLayout();
  private StatisticsTableHeader myStatisticsHeader;
  private StatisticsHeaderHost myStatisticsHeaderHost;
  /** The value of the open multi-cell edit, which both tables show in the selected cells. */
  private final MultiCellEditValue myEditedValue = new MultiCellEditValue();
  private final GridSelectionGrower myGrower;
  private boolean myWasAutomaticallyTransposed;
  private final TableCellImageCache myCellImageCache;
  /** The adapters of the grid's cell renderers and the cell colors, which every table of the view shares. */
  private final ViewCellRenderers myCellRenderers;
  /** The grid's cell editors and what the grid allows an edit, which every table of the view shares. */
  private final ViewCellEditors myCellEditors;
  /** What the column headers of every table of the view show of the grid's state. */
  private final ColumnHeaderAppearance myHeaderAppearance;
  private final RowHeightModel myRowHeights = new RowHeightModel();
  /** Blocks a column drag in the header of every table of the view while the grid writes a column move. */
  private final ColumnReorderingBlock myColumnReorderingBlock = new ColumnReorderingBlock();
  /** The data model both tables show, which the view replaces on a transpose. */
  private GridTableModel myDataModel;
  /** The row selection both tables share. */
  private final ListSelectionModel myRowSelection = new DefaultListSelectionModel();
  private final TableRowOrder myRowOrder;
  /** What Escape does in every table of the view, once the grid has registered it. */
  private @Nullable AbstractAction myEscapeAction;
  /** Whether a check that the focus follows the lead is waiting for the event queue. */
  private boolean myFocusCheckQueued;
  /** Whether the view is taking new columns; the grid tells its listeners once it has applied them. */
  private boolean myAddingColumns;
  private boolean myDisposed;
  private boolean myShowRowNumbers;
  /** The row header the scroll pane shows, beside the pinned table while one shows; this view creates and disposes it. */
  private @Nullable GridRowHeader myRowHeader;
  /** Tells the grid of a new structure of the data model; the row order tells it of a change of rows. */
  private final TableModelListener myDataStructureListener = this::dataModelChanged;
  /** Reports the grid active when a cell editor of any table of the view starts or stops. */
  private final PropertyChangeListener myCellEditorListener = this::cellEditorChanged;

  public TableResultView(@NotNull DataGrid grid, @NotNull ActionGroup columnHeaderActions, @NotNull ActionGroup rowHeaderActions) {
    myGrid = grid;
    myGridModel = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS);
    myHeaderPopups = new TableHeaderPopups(grid, columnHeaderActions, rowHeaderActions);
    myDataModel = new RegularGridTableModel(myGridModel);
    // Before any table listens: Swing tells the first listener last, so the grid hears of a new structure after every table took it.
    myDataModel.addTableModelListener(myDataStructureListener);
    myColumns = new GridColumns(grid, new ColumnsHost(), myDataModel);
    // Every table selects through these two models, so their mode is set here once.
    myRowSelection.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    myColumns.getColumnModel().getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    myDisplayedColumns = new DisplayedColumns(myColumns.getColumnModel(), myColumns);
    myMainSlice = new GridColumnSlice(myColumns.getColumnModel(), myColumns, ColumnsToShow.UNPINNED, myDisplayedColumns);
    myCellRenderers = new ViewCellRenderers(grid);
    myCellEditors = new ViewCellEditors(grid, myEditedValue);
    myHeaderAppearance = new ColumnHeaderAppearance(grid);
    myMainTable = new GridTable(sharedTableParts(), myMainSlice);
    myRawIndexConverter = new TableRawIndexConverter(myMainTable, myColumns.getColumnModel());
    myTables = new MutableViewTables(grid, myMainTable, myMainSlice);
    myGridSelectionEvents = new GridSelectionEvents(myRowSelection, myColumns.getColumnModel());
    mySelectionModel = new TableSelectionModel(grid, myRowSelection, myColumns.getColumnModel(),
                                               myGridSelectionEvents, myDataModel, this);
    myRowOrder = new TableRowOrder(grid, myRowSelection, myColumns.getColumnModel(), myRowHeights, myTables, mySelectionModel);
    // One cache for the cell images of every table of the view. It sizes itself by the main table's visible area, the cells that scroll.
    myCellImageCache = new TableCellImageCache(myMainTable, this);
    myFloatingToolbar = new TableFloatingToolbarController(grid, grid.getCoroutineScope());
    myFrozenColumnsController =
      new FrozenColumnsController(myTables, myColumns.getColumnModel(), myDisplayedColumns, myRowSelection, grid);
    myHeaderClicks = new TableHeaderClicks(grid, mySelectionModel, myColumns.getColumnModel(), myDisplayedColumns, myMainTable,
                                           myHeaderPopups);
    myValueEditorCells = new ValueEditorCells(grid);
    myMainTable.installHost(adoptTable(myMainTable), myRowHeights, myCellImageCache, myEditedValue, myRowOrder.getSorter());
    MoveColumnListener mainMoveColumnListener = createMoveColumnListener();
    myMainTable.getTableHeader().addMouseListener(mainMoveColumnListener);
    myMainSlice.addColumnModelListener(mainMoveColumnListener);
    myScrollPane = TableResultViewFactory.createScrollPane(grid, myMainTable, mySelectionModel, this);
    // Every table selects through these two models, so one listener repaints the scroll pane for a change in either table.
    ListSelectionListener scrollPaneRepaint = ignored -> myScrollPane.repaint();
    myRowSelection.addListSelectionListener(scrollPaneRepaint);
    myColumns.getColumnModel().getSelectionModel().addListSelectionListener(scrollPaneRepaint);
    // The grid looks for both on the view's component.
    getComponent().putClientProperty(SelectionModelUtil.SELECTION_MODEL_KEY, mySelectionModel);
    getComponent().putClientProperty(GridScrollPositionManager.SCROLL_POSITION_MANAGER_KEY,
                                     new TableScrollPositionManager(myMainTable, myRowSelection, grid));
    myGrower = new GridSelectionGrower(grid, this);
    // Both tables select through the view's column model, so this covers both.
    myColumns.getColumnModel().getSelectionModel().addListSelectionListener(e -> myGrower.reset());
    // The main table scrolls to the lead in its own columns only; a lead in the pinned table still needs its row in view.
    myGridSelectionEvents.addListener(isAdjusting -> {
      if (!isAdjusting) myFrozenColumnsController.revealPinnedLeadRow();
    });
    // A lead that moves into the other table, as with Find Next or a header click there, takes the focus along.
    // A column move or hide shifts the lead before the slices follow, so the lead's table is known only afterwards.
    // Such a move requests no focus, so nothing asks getPreferredFocusedComponent for the lead's table.
    myColumns.getColumnModel().getSelectionModel().addListSelectionListener(e -> {
      if (e.getValueIsAdjusting() || myFocusCheckQueued) return;
      myFocusCheckQueued = true;
      ApplicationManager.getApplication().invokeLater(() -> {
        myFocusCheckQueued = false;
        moveFocusToLeadColumnTable();
      }, ModalityState.any(), ignored -> myDisposed);
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
    myColumnLayout = GridHelper.get(myGrid).createColumnLayout(new TableColumnLayoutContext(), myGrid);
    myMainTable.installColumnLayout(myColumnLayout);
  }

  @TestOnly
  void initializeColumnLayoutForTests() {
    initializeColumnLayout();
  }

  /** What a new table shares with the view's other tables, built for each table: a transpose replaces the data model. */
  private @NotNull SharedTableParts sharedTableParts() {
    // Named as its parameter, since the grid model next to it has the same type.
    GridModel<GridRow, GridColumn> databaseModel = myGrid.getDataModel(DataAccessType.DATABASE_DATA);
    return new SharedTableParts(myDataModel, myGridModel, databaseModel, myRowSelection, myColumns.getColumnCache(), myCellRenderers,
                                myCellEditors, myHeaderAppearance, myGrid.getColorsScheme(), myGrid.getAutoscrollLocker(),
                                myColumnReorderingBlock);
  }

  /** Sets up a table of this view, the main or the pinned one, and creates the host it reports to. */
  private GridTableHost adoptTable(GridTable table) {
    // First, before the view's other mouse listeners, so they get the click after it may have consumed it.
    if (myGrid instanceof DataGridWithNestedTables nested && nested.isNestedTableSupportEnabled()) {
      table.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          if (e.getButton() == MouseEvent.BUTTON3) return;
          int viewRow = table.rowAtPoint(e.getPoint());
          int viewColumn = table.columnAtPoint(e.getPoint());
          int modelRow = table.getRawIndexConverter().row2Model().applyAsInt(viewRow);
          int modelColumn = table.getRawIndexConverter().column2Model().applyAsInt(viewColumn);
          if (modelRow < 0 || modelColumn < 0) return;
          if (nested.onCellClick(ModelIndex.forRow(myGrid, modelRow), ModelIndex.forColumn(myGrid, modelColumn))) {
            e.consume();
          }
        }
      });
    }
    table.putClientProperty(Magnificator.CLIENT_PROPERTY_KEY, (Magnificator)(scale, at) -> magnify(table, scale, at));
    myFloatingToolbar.attach(table);
    if (myEscapeAction != null) table.registerEscapeAction(myEscapeAction);
    table.getEmptyText().setText(""); //DatabaseMessages.message("table.result.data.loading"));
    table.setCellSelectionEnabled(true);
    table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
    table.setBackground(myGrid.getColorsScheme().getDefaultBackground());
    // fixes missing (or plain white) grid on mac
    table.setShowGrid(true);
    table.addPropertyChangeListener(GridTableCellEditor.TABLE_CELL_EDITOR_PROPERTY, myCellEditorListener);
    // Focus that reaches a table which doesn't show the lead cell goes on to the table that does.
    // This stays: Tab picks a table by its place, and an activated window restores a focus owner that may no longer show the lead.
    table.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent e) {
        GridTable leadTable = leadColumnTable();
        if (leadTable != table && !e.isTemporary()) {
          IdeFocusManager.getInstance(myGrid.getProject()).requestFocus(leadTable, false);
        }
      }
    });
    table.addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(@NotNull MouseEvent e) {
        if (!SwingUtilities.isRightMouseButton(e)) return;
        int rowIndex = table.isTransposed() ? table.columnAtPoint(e.getPoint()) : table.rowAtPoint(e.getPoint());
        int columnIndex = table.isTransposed() ? table.rowAtPoint(e.getPoint()) : table.columnAtPoint(e.getPoint());
        if (rowIndex < 0 || columnIndex < 0 || table.isCellSelected(rowIndex, columnIndex)) return;
        int modelRow = table.getRawIndexConverter().row2Model().applyAsInt(rowIndex);
        int modelColumn = table.getRawIndexConverter().column2Model().applyAsInt(columnIndex);
        myGrid.getSelectionModel().setSelection(ModelIndexSet.forRows(myGrid, modelRow),
                                                       ModelIndexSet.forColumns(myGrid, modelColumn));
      }
    });
    myFrozenColumnsController.installColumnNavigationActions(table);
    myFrozenColumnsController.addDragAnchorListener(table);
    // Before the view's column drag listener, where the table's own header listener was.
    table.getTableHeader().addMouseListener(myHeaderClicks);
    return new TableHost(table);
  }

  /** A header listener that applies a column drag in either table to the grid's data, in the order of the view's column model. */
  private @NotNull MoveColumnListener createMoveColumnListener() {
    return new MoveColumnListener(myGrid, myColumnReorderingBlock, myColumns, myRowOrder);
  }

  private List<GridTable> tables() {
    return myTables.all();
  }

  private void forEachTable(Consumer<GridTable> operation) {
    for (GridTable table : myTables.all()) operation.accept(table);
  }

  /** The scroll pane that holds both tables; {@link TableResultViewFactory#wrap} places it in the grid layout. */
  @Override
  public @NotNull JComponent getComponent() { return myScrollPane; }

  /** {@link #getComponent()}, typed. */
  @NotNull TableScrollPane getScrollPane() { return myScrollPane; }

  /** The table that shows the lead cell. A traversal policy rejects a scroll pane, so the platform focuses a table. */
  @Override
  public @NotNull JComponent getPreferredFocusedComponent() { return leadColumnTable(); }

  /** Both tables, the row numbers and the headers are in the scroll pane. */
  @Override
  public boolean isViewComponent(@NotNull Component component) {
    return SwingUtilities.isDescendingFrom(component, myScrollPane);
  }

  /** Whether either table has the focus; the pinned table shows cells of the view too. */
  @Override
  public boolean hasFocus() {
    for (GridTable table : tables()) {
      if (table.hasFocus()) return true;
    }
    return false;
  }

  /** A pinned table created later gets it too. */
  @Override
  public void registerEscapeAction(@NotNull AbstractAction action) {
    myEscapeAction = action;
    forEachTable(table -> table.registerEscapeAction(action));
  }

  @Override
  public boolean isEditing() { return editingTable() != null; }

  private @Nullable GridTable editingTable() {
    if (myMainTable.isEditing()) return myMainTable;
    GridTable pinned = myTables.getPinned();
    return pinned != null && pinned.isEditing() ? pinned : null;
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
   * Brings the pinned table in line with the visible pinned columns, without changing the pins. It creates the table for the first
   * such column and removes it after the last. A pinned column that just came into view keeps its on-screen width, which the column
   * layout then leaves alone. The slices follow shown, hidden and rebuilt columns themselves, but a returning pinned column needs a
   * pinned table to show it.
   */
  private void updatePinnedTable() {
    myColumns.lockNewlyShownPinnedColumns();
    if (!myColumns.hasVisiblePinnedColumns()) {
      removePinnedTable();
      return;
    }
    // A table created after dispose would stay registered for disposal with nothing to dispose it.
    if (myDisposed) return;
    GridTable pinned = myTables.getPinned();
    if (pinned == null) pinned = addPinnedTable();
    // Again after each change, so the pinned table follows the main table's fonts, lines and row height.
    pinned.applyAppearance(myMainTable.captureAppearance());
    // The new fonts may change the size of the grid.
    myGrid.trueLayout();
    myFrozenColumnsController.showPinnedTable();
  }

  /**
   * Creates the pinned table, with a slice of its own. JTable's constructor clears the selection of the column model a table starts on.
   * So the table starts on an empty one and gets its slice after the setup. Both tables share the data model, the row order, the row
   * selection and, through their slices, the view's column model.
   */
  private @NotNull GridTable addPinnedTable() {
    GridColumnSlice pinnedSlice = new GridColumnSlice(myColumns.getColumnModel(), myColumns, ColumnsToShow.PINNED, myDisplayedColumns);
    FrozenStripTable table = new FrozenStripTable(sharedTableParts(), new DefaultTableColumnModel());
    table.installHost(adoptTable(table), myRowHeights, myCellImageCache, myEditedValue, myRowOrder.getSorter());
    table.setColumnModel(pinnedSlice);
    for (MouseListener listener : myCellMouseListeners) table.addMouseListener(listener);
    for (MouseMotionListener listener : myCellMotionListeners) table.addMouseMotionListener(listener);
    for (FocusListener listener : myCellFocusListeners) table.addFocusListener(listener);
    // Mutable document grids update their source on mouse release, when the slice has moved the column in the view's column model.
    MoveColumnListener moveColumnListener = createMoveColumnListener();
    table.getTableHeader().addMouseListener(moveColumnListener);
    pinnedSlice.addColumnModelListener(moveColumnListener);
    myPinnedMoveColumnListener = moveColumnListener;
    myTables.setPinned(table, pinnedSlice);
    return table;
  }

  /**
   * Removes the pinned table and its slice. It removes what {@link #adoptTable} shared with the view: the header, cell and focus listeners
   * and the controller's drag-anchor listener. Then it detaches the table and drops it. The cell image cache starts over, so it keeps no
   * images of cells that no table shows.
   */
  private void removePinnedTable() {
    GridTable table = myTables.getPinned();
    if (table == null) return;
    myFrozenColumnsController.pinnedTableDropping();
    GridColumnSlice pinnedSlice = Objects.requireNonNull(myTables.removePinned());
    MoveColumnListener moveColumnListener = myPinnedMoveColumnListener;
    myPinnedMoveColumnListener = null;
    if (moveColumnListener != null) {
      table.getTableHeader().removeMouseListener(moveColumnListener);
      pinnedSlice.removeColumnModelListener(moveColumnListener);
    }
    table.getTableHeader().removeMouseListener(myHeaderClicks);
    for (MouseListener listener : myCellMouseListeners) table.removeMouseListener(listener);
    for (MouseMotionListener listener : myCellMotionListeners) table.removeMouseMotionListener(listener);
    for (FocusListener listener : myCellFocusListeners) table.removeFocusListener(listener);
    myFrozenColumnsController.removeDragAnchorListener(table);
    table.removePropertyChangeListener(GridTableCellEditor.TABLE_CELL_EDITOR_PROPERTY, myCellEditorListener);
    // JTable and TableExpandableItemsHandler follow the row selection; a model of its own unregisters them.
    table.setSelectionModel(new DefaultListSelectionModel());
    // The same for the slice; both swaps also keep detachModel, which clears the table's selection, from clearing the view's.
    table.setColumnModel(new DefaultTableColumnModel());
    table.detachModel();
    Disposer.dispose(table);
    myFloatingToolbar.detach(table);
    pinnedSlice.dispose();
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

  /** Shows {@code header}, or no row header when null, through the pinned-column controller, and disposes the header it replaces. */
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
    GridRowHeader header = myGrid.createRowHeader(this);
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
    removePinnedTable();
    Disposer.dispose(myFloatingToolbar);
    Disposer.dispose(myColumns);
    Disposer.dispose(myMainTable);
    myMainSlice.dispose();
    myEditedValue.setValue(null);
  }

  /** The width the columns of both tables share: the scroll pane's width without the row numbers and the vertical scroll bar. */
  int getAvailableColumnsWidth() { return myFrozenColumnsController.getAvailableColumnsWidth(); }

  @Override
  public @NotNull ModelIndex<GridColumn> getContextColumn() { return myHeaderPopups.getContextColumn(); }

  @TestOnly
  public void setContextColumn(@NotNull ModelIndex<GridColumn> column) { myHeaderPopups.setContextColumnForTests(column); }

  /** The main table, which shows the unpinned columns. */
  @TestOnly
  @NotNull GridTable getPrimaryTable() { return myMainTable; }

  @TestOnly
  GridTable getPinnedTable() { return myTables.getPinned(); }

  @Override
  public void setShowHorizontalLines(boolean show) {
    forEachTable(table -> table.setShowHorizontalLines(show));
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
  public void setTransparentColumnHeaderBackground(boolean transparent) {
    forEachTable(table -> table.setTransparentColumnHeaderBackground(transparent));
  }

  @Override
  public void setAllowMultilineLabel(boolean allow) {
    forEachTable(table -> table.setAllowMultilineLabel(allow));
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

  /**
   * Writes {@code v} to the cell, or to every selected cell while multi-cell editing is allowed. After a commit by
   * {@code committingEditor}, the selection moves on unless the editor says otherwise.
   */
  private void writeValue(@Nullable Object v,
                          @NotNull ModelIndex<GridRow> row,
                          @NotNull ModelIndex<GridColumn> column,
                          boolean allowImmediateUpdate,
                          @NotNull GridRequestSource source,
                          @Nullable Object metadata,
                          @Nullable GridTableCellEditor committingEditor) {
    SelectionModel<GridRow, GridColumn> selection = myGrid.getSelectionModel();
    boolean multi = isMultiEditingAllowed();
    ModelIndexSet<GridRow> rows = multi ? selection.getSelectedRows() : ModelIndexSet.forRows(myGrid, List.of(row));
    ModelIndexSet<GridColumn> columns =
      multi ? selection.getSelectedColumns() : ModelIndexSet.forColumns(myGrid, List.of(column));
    ViewIndex<GridRow> lastRow = rows.toView(myGrid).last();
    ViewIndex<GridColumn> lastColumn = columns.toView(myGrid).last();
    Runnable moveToNextCell =
      committingEditor != null && committingEditor.shouldMoveFocus() ? () -> moveToNextCellLater(lastRow, lastColumn) : null;
    myGrid.setValueAt(rows, columns, v, allowImmediateUpdate, moveToNextCell, source, metadata);
  }

  private void moveToNextCellLater(@NotNull ViewIndex<GridRow> rowIndex, @NotNull ViewIndex<GridColumn> colIndex) {
    IdeFocusManager.findInstanceByComponent(myScrollPane).doWhenFocusSettlesDown(
      // doWhenFocusSettlesDown causes "Write-unsafe context" so we need to call invokeLater
      () -> ApplicationManager.getApplication().invokeLater(() -> moveToNextCell(rowIndex, colIndex)));
  }

  private void moveToNextCell(@NotNull ViewIndex<GridRow> rowIndex, @NotNull ViewIndex<GridColumn> colIndex) {
    SelectionModel<GridRow, GridColumn> selectionModel = myGrid.getSelectionModel();
    if (selectionModel.getSelectedRowCount() != 1 || selectionModel.getSelectedColumnCount() != 1 ||
        !ApplicationManager.getApplication().isUnitTestMode() && (!UIUtil.isFocusAncestor(myScrollPane) || isEditing())) {
      return;
    }

    // if selection has already been changed by Tab or Shift-Tab we don't need to change it
    if (!Comparing.equal(selectionModel.getSelectedRow(), rowIndex.toModel(myGrid)) ||
        !Comparing.equal(selectionModel.getSelectedColumn(), colIndex.toModel(myGrid))) {
      return;
    }

    if (GridUtil.isInsertedRow(myGrid, rowIndex.toModel(myGrid))) {
      ViewIndex<GridColumn> nextColumn = ViewIndex.forColumn(myGrid, colIndex.asInteger() + 1);
      colIndex = nextColumn.isValid(myGrid) ? nextColumn : colIndex;
    }
    else {
      ViewIndex<GridRow> nextRow = ViewIndex.forRow(myGrid, rowIndex.asInteger() + 1);
      rowIndex = nextRow.isValid(myGrid) ? nextRow : rowIndex;
    }

    GridUtil.scrollToLocally(myGrid, rowIndex, colIndex);
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
    LOG.assertTrue(myTables.getPinned() == null, "A transposed grid has no pinned table");
    myDataModel.removeTableModelListener(myDataStructureListener);
    myDataModel = transposed ? new TransposedGridTableModel(myGridModel) : new RegularGridTableModel(myGridModel);
    myDataModel.addTableModelListener(myDataStructureListener);
    GridUtil.saveAndRestoreSelection(myGrid, () -> {
      myColumns.setDataModel(myDataModel);
      mySelectionModel.setDataModel(myDataModel);
      myMainTable.setDataModel(myDataModel);
      myGrid.updateSortKeysFromColumnAttributes();
      myDataModel.fireTableDataChanged();
      // A header for the new model, before the grid pins columns again and the pinned table gets the row numbers beside it.
      replaceRowHeaderForNewDataModel();
      myColumns.createDefaultColumnsFromModel();
      if (!transposed) {
        // The grid applies its column order, pins and widths again; then the untransposed widths come back.
        if (myGrid instanceof ColumnOrderRestorer restorer) restorer.refreshColumnLayout();
        myColumns.restoreUntransposedColumnWidths();
      }
    });
  }

  @Override
  public @Nullable JScrollBar getVerticalScrollBar() {
    return myMainTable.getVerticalScrollBar();
  }

  @Override
  public @Nullable JScrollBar getHorizontalScrollBar() {
    return myMainTable.getHorizontalScrollBar();
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
    Point viewPosition = myScrollPane.getViewport().getViewPosition();
    int row = indexOrZero(myMainTable.rowAtPoint(viewPosition), myMainTable.getRowCount());
    int position =
      indexOrZero(positionAt(myMainTable, myMainTable.columnAtPoint(viewPosition)), myColumns.getColumnModel().getColumnCount());
    GridTable pinned = myTables.getPinned();
    boolean transposed = isTransposed();
    if (pinned != null && pinned.getColumnCount() > 0 && !transposed) {
      // The strip scrolls sideways on its own when it overflows, so take its column at the left edge of its viewport.
      JViewport stripViewport = ObjectUtils.tryCast(pinned.getParent(), JViewport.class);
      int stripX = stripViewport == null ? 0 : stripViewport.getViewPosition().x;
      int stripLocalIndex = indexOrZero(pinned.columnAtPoint(new Point(stripX, 0)), pinned.getColumnCount());
      int stripPosition = positionAt(pinned, stripLocalIndex);
      if (stripPosition >= 0) position = stripPosition;
    }
    return new Pair<>(ViewIndex.forRow(myGrid, transposed ? position : row),
                      ViewIndex.forColumn(myGrid, transposed ? row : position));
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
  public void addSelectionChangedListener(@NotNull ResultViewSelectionListener listener) {
    myGridSelectionEvents.addListener(listener);
  }

  @Override
  public void restoreColumnsOrder(Map<Integer, ModelIndex<GridColumn>> expectedToModel) {
    // The order lives in the view's columns, which both tables move.
    myColumns.restoreColumnsOrder(expectedToModel);
  }

  /**
   * The model column at 1-based {@code columnNumber} among the visible columns, as the user types it in Go to Row; -1 when out of range.
   */
  @NotNull ModelIndex<GridColumn> modelColumnOfNumber(int columnNumber) {
    int modelIndex = columnNumber < 1 || columnNumber >= getViewColumnCount() + 1
                     ? -1
                     : myRawIndexConverter.column2Model().applyAsInt(columnNumber - 1);
    return ModelIndex.forColumn(myGrid, modelIndex);
  }

  /** The 0-based row index of 1-based {@code rowNumber}, as the user types it in Go to Row, or -1. The row may sit on another page. */
  int rowIndexOfNumber(int rowNumber) {
    return rowNumber < 1 ? -1 : rowNumber - 1;
  }

  /**
   * Selects and shows the row's cell in the first column the user sees, which is the first pinned one while columns are pinned. The caller
   * can be a data request's thread, so the view's displayed column order is read on the EDT.
   */
  @Override
  public void showFirstCell(int rowNumOnCurrentPage) {
    ApplicationManager.getApplication().invokeLater(() -> {
      Integer first = myDisplayedColumns.order().first();
      int firstShown = first != null ? first : 0;
      myGrid.showCell(rowIndexOfNumber(rowNumOnCurrentPage), ViewIndex.forColumn(myGrid, firstShown).toModel(myGrid));
    }, ignored -> myDisposed);
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
    if (!viewRow.isValid(myGrid) || !viewColumn.isValid(myGrid)) return null;
    boolean transposed = isTransposed();
    int tableRow = (transposed ? viewColumn : viewRow).asInteger();
    int position = (transposed ? viewRow : viewColumn).asInteger();
    GridTable table = tableAt(position);
    int localIndex = localIndexAt(table, position);
    return localIndex < 0 ? null : table.getCellRendererComponent(tableRow, localIndex, forDisplay);
  }

  @Override
  public void setColumnEnabled(@NotNull ModelIndex<GridColumn> columnIdx, boolean enabled) {
    myGridSelectionEvents.runAsOneChange(() -> {
      // The table that shows the column gains or loses it through its slice.
      myColumns.setColumnEnabled(columnIdx, enabled);
      updatePinnedTable();
    });
  }

  @Override
  public void setColumnsEnabled(@NotNull List<ModelIndex<GridColumn>> columns, boolean enabled) {
    myGridSelectionEvents.runAsOneChange(() -> {
      myColumns.setColumnsEnabled(columns, enabled);
      updatePinnedTable();
    });
  }

  @Override
  public void setRowEnabled(@NotNull ModelIndex<GridRow> rowIdx, boolean enabled) {
    myGridSelectionEvents.runAsOneChange(() -> myColumns.setRowEnabled(rowIdx, enabled));
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
    myMainTable.orderingAndVisibilityChanged();
  }

  @Override
  public @NotNull RawIndexConverter getRawIndexConverter() {
    return myRawIndexConverter;
  }

  @Override
  public void addMouseListenerToComponents(@NotNull MouseListener listener) {
    myScrollPane.addMouseListener(listener);
    addMouseListenerToCells(listener);
  }

  /** Every table gets the listener, a pinned table created later too. */
  @Override
  public void addMouseListenerToCells(@NotNull MouseListener listener) {
    myCellMouseListeners.add(listener);
    forEachTable(table -> table.addMouseListener(listener));
  }

  /** Every table gets the listener, a pinned table created later too. */
  @Override
  public void addMouseMotionListenerToCells(@NotNull MouseMotionListener listener) {
    myCellMotionListeners.add(listener);
    forEachTable(table -> table.addMouseMotionListener(listener));
  }

  /** Every table gets the listener, a pinned table created later too, until {@code parent} goes. */
  @Override
  public void addFocusListenerToCells(@NotNull FocusListener listener, @NotNull Disposable parent) {
    myCellFocusListeners.add(listener);
    forEachTable(table -> table.addFocusListener(listener));
    Disposer.register(parent, () -> {
      myCellFocusListeners.remove(listener);
      forEachTable(table -> table.removeFocusListener(listener));
    });
  }

  @Override
  public boolean supportsCustomSearchSession() {
    return true;
  }

  @Override
  public @NotNull DataGridSearchSession createSearchSession(@Nullable FindModel findModel, @Nullable Component previousFilterComponent) {
    FindModel newFindModel = new FindModel();
    if (findModel != null) newFindModel.copyFrom(findModel);
    DataGridSearchSession.configureFindModel(myGrid, newFindModel);
    return new DataGridSearchSession(myGrid.getProject(), myGrid, newFindModel, previousFilterComponent);
  }

  @Override
  public void searchSessionStarted(@NotNull SearchSession searchSession) {
    if (!(searchSession instanceof GridSearchSession<?, ?> session)) return;
    session.addListener(this, this);
  }

  /** Takes a point in the main table's coordinates; the tables share their rows, so the row is the same in both. */
  public int rowAtPoint(@NotNull Point point) {
    return myMainTable.rowAtPoint(point);
  }

  @Override
  public boolean isViewModified() {
    for (int position = 0; position < myColumns.getColumnModel().getColumnCount(); position++) {
      ViewIndex<?> viewIndex =
        isTransposed() ? ViewIndex.forRow(myGrid, position) : ViewIndex.forColumn(myGrid, position);
      ModelIndex<?> modelIndex = viewIndex.toModel(myGrid);
      if (modelIndex.isValid(myGrid) && viewIndex.isValid(myGrid) && modelIndex.asInteger() != viewIndex.asInteger()) {
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
    columnRenderingChanged(columnIdx);
  }

  @Override
  public void displayTypeUpdated(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull DisplayType displayType) {
    columnRenderingChanged(columnIdx);
  }

  /**
   * Drops the renderer cache of the column's first displayed cell and the whole cell image cache. Then lays out and repaints the table
   * that shows the column. A column that no table shows changes nothing.
   */
  private void columnRenderingChanged(@NotNull ModelIndex<GridColumn> column) {
    // While transposed, the column is a table row, and the first cell is in the first table column.
    int position = myRawIndexConverter.column2View().applyAsInt(column.value);
    int firstRow = myRawIndexConverter.row2Model().applyAsInt(0);
    // -1 without rows, or while createDefaultColumnsFromModel sets a binary display type before creating the column (DBE-16996).
    if (position < 0 || firstRow < 0) return;
    myCellRenderers.clearRendererCache(ModelIndex.forRow(myGrid, firstRow), column);
    myCellImageCache.reset();
    GridTable table = myTables.tableOf(ViewIndex.forColumn(myGrid, position));
    table.revalidate();
    table.repaint();
  }

  /** Takes a row of the main table; both tables show the same rows. */
  void setRowHeight(int row, int rowHeight) {
    if (row < 0 || row >= myMainTable.getRowCount()) return;
    myRowHeights.setHeight(myMainTable.convertRowIndexToModel(row), rowHeight);
  }

  /** The height that fits every cell of {@code row} in every table, which share their rows, and at least the default row height. */
  int expandedRowHeight(int row) {
    int height = myMainTable.getRowHeight();
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
    myAddingColumns = true;
    try {
      // The pinned table gets the model event from the shared data model, and the new columns through its slice.
      myColumns.columnAttributesUpdated();
      myMainTable.columnsAdded(columnIndices);
      myCellImageCache.reset();
      updatePinnedTable();
    }
    finally {
      myAddingColumns = false;
    }
  }

  @Override
  public void columnAttributesUpdated() {
    myColumns.columnAttributesUpdated();
    updatePinnedTable();
  }

  @Override
  public void columnsRemoved(ModelIndexSet<GridColumn> columns) {
    myMainTable.columnsRemoved(columns);
    if (!isTransposed()) myColumns.createDefaultColumnsFromModel();
    myCellImageCache.reset();
    updatePinnedTable();
  }

  @Override
  public void rowsAdded(ModelIndexSet<GridRow> rows) {
    // The pinned table gets the model event from the shared data model.
    myMainTable.rowsAdded(rows);
    if (isTransposed()) myColumns.createDefaultColumnsFromModel();
    myColumnLayout.newRowsAdded(rows);
  }

  @Override
  public void afterLastRowAdded() {
    DataGridSettings settings = GridUtil.getSettings(myGrid);
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
    SingleRowModeHelper.expandRowIfNeeded(myGrid);
    // Every row is loaded, so the row numbers take their final width.
    resizeRowHeader();
  }

  @Override
  public void addSpaceForHorizontalScrollbar(boolean addSpace) {
    myScrollPane.addSpaceForHorizontalScrollbar(addSpace);
  }

  @Override
  public void expandMultilineRows(boolean expand) {
    myMainTable.expandMultilineRows(expand);
  }

  @Override
  public void rowsRemoved(ModelIndexSet<GridRow> rows) {
    myMainTable.rowsRemoved(rows);
    if (isTransposed()) myColumns.createDefaultColumnsFromModel();
  }

  @Override
  public void cellsUpdated(ModelIndexSet<GridRow> rows, ModelIndexSet<GridColumn> columns, @Nullable GridRequestSource.RequestPlace place) {
    myMainTable.cellsUpdated(rows, columns, place);
  }

  @Override
  public void changeSelectedColumnsWidth(int delta) {
    ViewColumnModel columnModel = myColumns.getColumnModel();
    int[] selectedPositions = Arrays.stream(columnModel.getSelectedColumns())
      .filter(position -> position >= 0 && position < columnModel.getColumnCount()).toArray();
    int pinnedCount = 0;
    for (int position : selectedPositions) {
      if (isPinnedColumn(position)) pinnedCount++;
    }
    int constrainedDelta =
      myFrozenColumnsController.constrainSelectedColumnWidthDelta(pinnedCount, selectedPositions.length - pinnedCount, delta);
    for (int position : selectedPositions) {
      TableResultViewColumn tableColumn = columnModel.getColumn(position);
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
    myMainTable.scrollColumnsIntoView(columns);
  }

  /**
   * Each unpinned column that, pinned alone beside {@code pinnedColumns}, leaves the main table a usable width; every one before the grid
   * has a width.
   */
  @Override
  public @NotNull Set<ModelIndex<GridColumn>> columnsThatCanBePinned(@NotNull Set<ModelIndex<GridColumn>> pinnedColumns) {
    var candidates = new ArrayList<TableResultViewColumn>();
    int pinnedWidth = 0;
    int unpinnedWidth = 0;
    for (TableResultViewColumn column : myColumns.visibleColumns()) {
      int width = column.getPinnedWidth();
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
      int width = column.getPinnedWidth();
      if (availableWidth <= 0 || PinnedColumnsFit.fits(pinnedWidth + width, unpinnedWidth - width, availableWidth)) {
        result.add(modelIndexOf(column));
      }
    }
    return result;
  }

  private @NotNull ModelIndex<GridColumn> modelIndexOf(@NotNull TableResultViewColumn column) {
    return ModelIndex.forColumn(myGrid, column.getModelIndex());
  }

  /** Whether the visible columns of {@code pinnedColumns} leave the main table a usable width. */
  @Override
  public boolean canFitPinnedColumns(@NotNull Set<ModelIndex<GridColumn>> pinnedColumns) {
    int availableWidth = getAvailableColumnsWidth();
    if (availableWidth <= 0) return true;
    int pinnedWidth = 0;
    int unpinnedWidth = 0;
    for (TableResultViewColumn column : myColumns.visibleColumns()) {
      int width = column.getPinnedWidth();
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

  public void updateRowFilter() {
    myRowOrder.updateRowFilter();
  }

  @Override
  public @Nullable Color getCellBackground(@NotNull ViewIndex<GridRow> row, @NotNull ViewIndex<GridColumn> column, boolean selected) {
    return selected ? myMainTable.getSelectionBackground() : myCellRenderers.cellBackground(row.toModel(myGrid), column.toModel(myGrid));
  }

  @Override
  public @NotNull Color getCellForeground(boolean selected) {
    return myMainTable.getCellForeground(selected);
  }

  @Override
  public void reinitSettings() {
    // Once for every table, which share the grid's renderer factories and the cell image cache.
    GridCellRendererFactories.get(myGrid).reinitSettings();
    myCellImageCache.reset();
    forEachTable(table -> table.reinitSettings());
    myGrid.trueLayout();
  }

  /** Takes view indices of the main table. */
  @TestOnly
  public boolean editCellAt(int row, int column, EventObject e) {
    return myMainTable.editCellAt(row, column, e);
  }

  @Override
  public void createDefaultColumnsFromModel() {
    myColumns.createDefaultColumnsFromModel();
    updatePinnedTable();
  }

  /** Both tables share the main table's zoomed font. */
  @Override
  public @NotNull Font getCellFont() {
    return myMainTable.getFont();
  }

  /** Applies the scheme to every table and lays the grid out. The platform also tells each table directly. */
  @Override
  public void globalSchemeChange(@Nullable EditorColorsScheme scheme) {
    forEachTable(table -> table.globalSchemeChange(scheme));
    myGrid.trueLayout();
  }

  /** A pinch at {@code at} in {@code table} zooms every table, and returns where the pinched cell's point is after the zoom. */
  private @NotNull Point magnify(@NotNull GridTable table, double scale, @NotNull Point at) {
    int column = table.columnAtPoint(at);
    int row = table.rowAtPoint(at);
    Rectangle cellBefore = column < 0 || row < 0 ? table.getBounds() : table.getCellRect(row, column, true);
    if (cellBefore.width == 0 || cellBefore.height == 0) return at;

    double xFraction = (at.x - cellBefore.x) / (double)cellBefore.width;
    double yFraction = (at.y - cellBefore.y) / (double)cellBefore.height;

    changeFontSize(0, scale);

    Rectangle cellAfter = column < 0 || row < 0 ? table.getBounds() : table.getCellRect(row, column, true);
    return new Point((int)(cellAfter.x + cellAfter.width * xFraction), (int)(cellAfter.y + cellAfter.height * yFraction));
  }

  void changeFontSize(int increment, double scale) {
    forEachTable(table -> table.changeFontSize(increment, scale));
    myGrid.trueLayout();
  }

  /** Applies the settings to every table and lays the grid out. The platform also tells each table directly. */
  @Override
  public void uiSettingsChanged(@NotNull UISettings uiSettings) {
    forEachTable(table -> table.uiSettingsChanged(uiSettings));
    myGrid.trueLayout();
  }

  @Override
  public int getRowHeight() {
    return myMainTable.getRowHeight();
  }

  /** Both tables take their colors from the grid's color scheme. */
  @Override
  public @NotNull Color getForeground() {
    return myMainTable.getForeground();
  }

  @Override
  public @NotNull Color getBackground() {
    return myMainTable.getBackground();
  }

  /** The main table fills its viewport while there are no columns, so the empty grid paints its background there. */
  @Override
  public void updateCellsPreferredSize(boolean noColumns) {
    Container viewport = myMainTable.getParent();
    if (viewport != null) myMainTable.setPreferredSize(noColumns ? viewport.getSize() : null);
  }

  /** The color of the lines between cells. */
  public Color getGridColor() {
    return myMainTable.getGridColor();
  }

  /** The data model both tables show. */
  public GridTableModel getModel() {
    return myDataModel;
  }

  @Override
  public boolean isTransposed() {
    return myMainTable.isTransposed();
  }

  @Override
  public void onColumnHierarchyChanged() {
    GridColumnHeaderActions.updateColumnHierarchyDepth(myGrid);
  }

  /**
   * Tells the grid of a new structure; Swing calls this listener after every table. The row order reports row changes, and the grid
   * reports new columns itself.
   */
  private void dataModelChanged(@NotNull TableModelEvent event) {
    if (event.getFirstRow() != TableModelEvent.HEADER_ROW || myAddingColumns) return;
    myGrid.fireContentChanged(event instanceof GridTableModel.RequestedTableModelEvent requested ? requested.getPlace() : null);
  }

  private void cellEditorChanged(@NotNull PropertyChangeEvent event) {
    GridUtil.activeGridChanged(myGrid);
  }

  @Override
  public @Nullable ResultViewColumn getLayoutColumn(@NotNull ModelIndex<?> column) {
    return myColumns.shownColumn(column);
  }

  /** The same column as {@link #getLayoutColumn}: the table that shows a column shows the view's own column object. */
  @Override
  public @Nullable ResultViewColumn getColumnForPersistence(@NotNull ModelIndex<?> column) {
    return myColumns.shownColumn(column);
  }

  @Override
  public @Nullable ResultViewColumn getLayoutColumnForDataColumn(@NotNull ModelIndex<GridColumn> column) {
    // Layout columns of a transposed view are backed by rows, so a data column has no layout column of its own.
    return isTransposed() ? null : myColumns.shownColumn(column);
  }

  /** The model rows the view shows. A view index past the shown rows, as a row filter leaves, converts to no valid model row. */
  @Override
  public @NotNull ModelIndexSet<GridRow> getVisibleRows() {
    int rowCount = isTransposed() ? myDataModel.getColumnCount() : myDataModel.getRowCount();
    IntUnaryOperator toModel = myRawIndexConverter.row2Model();
    int[] modelIndices = new int[rowCount];
    for (int i = 0; i < rowCount; i++) {
      modelIndices[i] = toModel.applyAsInt(i);
    }
    return ModelIndexSet.forRows(myGrid, validIndices(ModelIndexSet.forRows(myGrid, modelIndices)));
  }

  @Override
  public @NotNull ModelIndexSet<GridColumn> getVisibleColumns() {
    int columnCount = getViewColumnCount();
    IntUnaryOperator toModel = myRawIndexConverter.column2Model();
    int[] modelIndices = new int[columnCount];
    for (int i = 0; i < columnCount; i++) {
      modelIndices[i] = toModel.applyAsInt(i);
    }
    return ModelIndexSet.forColumns(myGrid, validIndices(ModelIndexSet.forColumns(myGrid, modelIndices)));
  }

  /** The indices of {@code indices} that are valid in the grid's data. */
  private int @NotNull [] validIndices(@NotNull ModelIndexSet<?> indices) {
    IntList validIndices = new IntArrayList(indices.size());
    for (ModelIndex<?> idx : indices.asIterable()) {
      if (idx.isValid(myGrid)) {
        validIndices.add(idx.asInteger());
      }
    }
    return validIndices.toIntArray();
  }

  @Override
  public int getViewColumnCount() {
    return isTransposed() ? getRowCount() : myColumns.getColumnModel().getColumnCount();
  }

  @Override
  public int getViewRowCount() {
    return myMainTable.getViewRowCount();
  }

  /**
   * Commits the open cell editor. The grid first checks the write and may ask the user to drop unsubmitted changes. A refusal keeps the
   * editor open.
   */
  @Override
  public boolean stopEditing() {
    GridTable editing = editingTable();
    TableCellEditor editor = editing == null ? null : editing.getCellEditor();
    if (editor == null) return true;

    ModelIndexSet<GridRow> editedRows;
    ModelIndexSet<GridColumn> editedColumns;
    if (myCellEditors.allowsMultiEdit(editor)) {
      // The grid's selection covers both tables of the view, in model indices.
      editedRows = myGrid.getSelectionModel().getSelectedRows();
      editedColumns = myGrid.getSelectionModel().getSelectedColumns();
    }
    else {
      int row = editing.getEditingRow();
      int localIndex = editing.getEditingColumn();
      editedRows = ModelIndexSet.forRows(myGrid, List.of(editing.modelRowAt(row, localIndex)));
      editedColumns = ModelIndexSet.forColumns(myGrid, List.of(editing.modelColumnAt(row, localIndex)));
    }

    // Only asks the grid; the commit itself writes through the table's host.
    return myGrid.isSafeToUpdate(editedRows, editedColumns, editor.getCellEditorValue()) && editor.stopCellEditing();
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
    if (myColumns.getColumnModel().getSelectionModel().getLeadSelectionIndex() < 0 || isEditing() || myTables.getPinned() == null) return;
    IdeFocusManager focusManager = IdeFocusManager.getInstance(myGrid.getProject());
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
    int leadPosition = myColumns.getColumnModel().getSelectionModel().getLeadSelectionIndex();
    return leadPosition < 0 ? myMainTable : tableAt(leadPosition);
  }

  /** Runs the table action for {@code move} in the table that shows the lead, as its key binding does. */
  @Override
  public void moveLead(@NotNull LeadMove move) {
    GridTable table = leadColumnTable();
    String actionName = switch (move) {
      case NEXT_ROW -> "selectNextRow";
      case PREVIOUS_ROW -> "selectPreviousRow";
      case NEXT_CELL -> "selectNextColumnCell";
      case PREVIOUS_CELL -> "selectPreviousColumnCell";
    };
    Action action = table.getActionMap().get(actionName);
    if (action != null) action.actionPerformed(new ActionEvent(table, ActionEvent.ACTION_PERFORMED, actionName));
  }

  @Override
  public void editSelectedCell() {
    int row = myRowSelection.getLeadSelectionIndex();
    int position = myColumns.getColumnModel().getSelectionModel().getLeadSelectionIndex();
    if (row < 0 || position < 0) return;
    GridTable target = tableAt(position);
    int localIndex = localIndexAt(target, position);
    if (localIndex >= 0) TableUtil.editCellAt(target, row, localIndex);
  }

  @Override
  public void editSelectedCellWithValue(Object value) {
    editSelectedCellWithValue(value, true);
  }

  public void editSelectedCellWithValue(Object value, boolean shouldMoveFocus) {
    int position = myColumns.getColumnModel().getSelectionModel().getLeadSelectionIndex();
    if (position < 0) return;
    // A cell editor opened in the table starts with value rather than the cell's value, and moves the focus on commit only if asked.
    GridTable table = tableAt(position);
    ClientProperty.put(table, GridTableCellEditor.CURRENT_VALUE_CLIENT_PROPERTY_KEY, value);
    ClientProperty.put(table, GridTableCellEditor.SUPPRESS_MOVE_FOCUS_CLIENT_PROPERTY_KEY, !shouldMoveFocus);
    try {
      editSelectedCell();
    }
    finally {
      ClientProperty.remove(table, GridTableCellEditor.CURRENT_VALUE_CLIENT_PROPERTY_KEY);
      ClientProperty.remove(table, GridTableCellEditor.SUPPRESS_MOVE_FOCUS_CLIENT_PROPERTY_KEY);
    }
  }

  @Override
  public boolean isMultiEditingAllowed() {
    GridTable editing = editingTable();
    return myCellEditors.allowsMultiEdit(editing == null ? null : editing.getCellEditor());
  }

  /** Takes view indices of the main table. */
  @TestOnly
  public void setValueAt(Object value, int viewRowIdx, int viewColumnIdx) {
    myMainTable.setValueAt(value, viewRowIdx, viewColumnIdx);
  }

  @Override
  public void resetScroll() {
    myMainTable.resetScroll();
  }

  @Override
  public boolean isHoveredRowBgHighlightingEnabled() {
    return myMainTable.isHoveredRowBgHighlightingEnabled();
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
      myStatisticsHeaderHost = new StatisticsHeaderHost(myMainTable, this, statisticsHeader);
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
  @NotNull JBTable rowHeaderTable() { return myMainTable; }

  /**
   * The view's column model: the shown columns in display order, pinned ones too. A position in it is a grid column's view index, or a grid
   * row's while transposed.
   */
  @NotNull TableColumnModel getViewColumnModel() { return myColumns.getColumnModel(); }

  void toggleSortOrder(@NotNull ModelIndex<GridColumn> column, boolean additive) {
    GridColumnHeaderActions.toggleSortOrder(myGrid, column, additive);
  }

  /** Opens the column popup of a transposed gutter, whose actions get the main table's data. */
  void invokeColumnPopup(@NotNull ModelIndex<GridColumn> column, @NotNull Component component, @NotNull Point point) {
    myHeaderPopups.showColumnPopup(column, myMainTable, component, point);
  }

  void invokeRowPopup(@NotNull Component component, int x, int y) { myHeaderPopups.showRowPopup(component, new Point(x, y)); }

  int getTextLineHeight() { return myMainTable.getTextLineHeight(); }

  /** The grid's selection model, which the view owns. */
  @NotNull TableSelectionModel selectionModel() { return mySelectionModel; }

  /*
   * The helpers below take positions in the view's column model, as Swing gives them. They ask the view's tables which table shows
   * which column. Untransposed, a position is a grid column's view index; transposed, the columns are the grid's rows, and nothing is
   * pinned.
   */

  private @NotNull GridTable tableAt(int position) { return myTables.tableAt(position); }

  private boolean isPinnedColumn(int position) { return tableAt(position) != myMainTable; }

  /** The index in {@code table} of the column at {@code position}, or -1 when the table doesn't show it. */
  private int localIndexAt(@NotNull GridTable table, int position) { return myTables.localIndexAt(table, position); }

  /** The position in the view's column model of the column at {@code localIndex} of {@code table}, or -1. */
  private int positionAt(@NotNull GridTable table, int localIndex) { return myTables.positionAt(table, localIndex); }

  /** The position of model column {@code modelColumn} in the view's column model, or -1. */
  private int positionOfModel(int modelColumn) {
    return myRawIndexConverter.columnIndexToView(modelColumn);
  }

  /**
   * What one table asks of the view. Its input goes through the pinned-column controller; edits and the value editor go to the view and
   * the grid.
   */
  private final class TableHost implements GridTableHost {
    private final GridTable myTable;

    private TableHost(GridTable table) { myTable = table; }

    @Override
    public boolean stopEditingBeforeSelectionChange(@Nullable ModelCell target) {
      return myFrozenColumnsController.stopEditingBeforeSelectionChange(myTable, target);
    }

    @Override
    public boolean beforeMouseEvent(@NotNull MouseEvent event) {
      return myFrozenColumnsController.beforeMouseEvent(myTable, event);
    }

    @Override
    public void processMouseMotionEvent(@NotNull MouseEvent event) {
      myFrozenColumnsController.processMouseMotionEvent(myTable, event);
    }

    @Override
    public int constrainColumnResize(@NotNull TableResultViewColumn column, int requestedWidth) {
      return myFrozenColumnsController.constrainColumnResize(myTable, column, requestedWidth);
    }

    @Override
    public void columnMarginChanged() { myFrozenColumnsController.columnMarginChanged(myTable); }

    @Override
    public void aboutToPaint() { updateCellImageCacheSize(); }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      sink.set(PlatformDataKeys.CONTEXT_MENU_POINT, myHeaderPopups.contextMenuPoint(myTable));
      // An open cell editor sits inside a table, and its own shortcuts must win over cell actions bound to F2 or Alt+Enter.
      sink.set(DatabaseDataKeys.DATA_GRID_ACTION_UI_STATE_KEY, GridActionUiState.capture(myGrid, !isEditing()));
    }

    @Override
    public void commitEditedValue(@Nullable Object value, @NotNull ModelCell cell) {
      // The table still holds its editor here: Swing writes the committed value before it removes the editor.
      GridTableCellEditor editor = ObjectUtils.tryCast(myTable.getCellEditor(), GridTableCellEditor.class);
      writeValue(value, cell.getRow(), cell.getColumn(), true, new GridRequestSource(new DataGridRequestPlace(myGrid)), null, editor);
    }

    @Override
    public @NotNull EditStart startEdit(@Nullable ModelCell cell, @Nullable EventObject startingEvent) {
      // JTable finishes only its own editor.
      for (GridTable other : tables()) {
        TableCellEditor editor = other == myTable ? null : other.getCellEditor();
        if (editor != null && !editor.stopCellEditing()) return EditStart.REFUSED;
      }
      if (cell == null || !myValueEditorCells.isValueEditorCell(cell.getRow(), cell.getColumn())) return EditStart.IN_CELL_EDITOR;
      if (myValueEditorCells.opensValueEditor(startingEvent)) openValueEditor();
      return EditStart.IN_VALUE_EDITOR;
    }

    private void openValueEditor() {
      AnAction action = ActionUtil.wrap("Console.TableResult.EditValueMaximized");
      // The cell's own table, so the action sees the data the table gives.
      AnActionEvent event = AnActionEvent.createEvent(action, DataManager.getInstance().getDataContext(myTable), null, ActionPlaces.UNKNOWN,
                                                      ActionUiKind.NONE, null);
      ShowEditMaximizedAction.getView(myGrid, event).open(provider -> provider instanceof ValueTabInfoProvider);
    }
  }

  /**
   * Passes requests from the view's columns to the grid, which keeps a column order for each view, and to the column layout. A removal
   * first ends any header drag.
   */
  private final class ColumnsHost implements GridColumnsHost {
    @Override
    public boolean applyColumnsDisplayOrder() {
      return myGrid instanceof ColumnOrderRestorer restorer && restorer.applyColumnsDisplayOrder(TableResultView.this);
    }

    @Override
    public void columnMoved(@NotNull ColumnMove move) {
      if (myGrid instanceof ColumnOrderRestorer restorer) restorer.columnMovedInView(TableResultView.this, move);
    }

    @Override
    public void columnsShown(@NotNull ModelIndexSet<?> columns) { myColumnLayout.columnsShown(columns); }

    @Override
    public void columnsRemoving() { clearDraggedColumns(); }
  }

  /**
   * What the column layout reads from the view: transpose, statistics mode, and header and cell widths from the table that shows each
   * column.
   */
  private final class TableColumnLayoutContext implements ColumnLayoutContext {
    @Override
    public boolean isTransposed() { return TableResultView.this.isTransposed(); }

    @Override
    public ResultViewColumn getLayoutColumn(@NotNull ModelIndex<?> column) { return getColumnForPersistence(column); }

    @Override
    public int getHeaderWidth(@NotNull ResultViewColumn column) {
      int position = positionOfModel(column.getModelIndex());
      GridTable table = tableAt(position);
      TableCellRenderer renderer = table.getTableHeader().getDefaultRenderer();
      Component component = renderer instanceof GridTable.MyCellRenderer header
                            ? header.getHeaderCellRendererComponent(column.getModelIndex(), false)
                            : renderer.getTableCellRendererComponent(table, column.getHeaderValue(), false, false, -1,
                                                                     localIndexAt(table, position));
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

  /** Listens to the main table's header only, so a pinned column's header goes unheard; for grids that pin no columns. */
  public void addColumnHeaderMouseListener(@NotNull MouseListener listener) {
    myMainTable.getTableHeader().addMouseListener(listener);
  }

  /*
   * The JTable-like getters from here on answer for the main table, unless one says otherwise. Rows, colors and the data model are the same
   * in both tables. Column counts, widths and the header drag leave out the pinned columns.
   */

  public int getColumnHeaderHeight() { return myMainTable.getTableHeader().getHeight(); }

  /** The column that a drag in the main table's header holds, or null; a drag in the pinned table's header doesn't count. */
  public @Nullable TableColumn getDraggedColumn() { return myMainTable.getTableHeader().getDraggedColumn(); }

  public int getHeaderDraggedDistance() { return myMainTable.getTableHeader().getDraggedDistance(); }

  /** Refreshes the tables as for a new structure of the data, which also forgets the row order and the row heights. */
  public void refreshCells() {
    myRowOrder.structureChanged();
    forEachTable(table -> table.tableChanged(null));
    myGrid.fireContentChanged(null);
  }

  /** Takes a point in the main table's coordinates and returns a main-table column index, which is not a grid index. */
  public int columnAtPoint(@NotNull Point point) { return myMainTable.columnAtPoint(point); }

  @Override
  public int getHoveredRow() {
    GridTable pinned = myTables.getPinned();
    int row = pinned == null ? -1 : TableHoverListener.getHoveredRow(pinned);
    return row >= 0 ? row : TableHoverListener.getHoveredRow(myMainTable);
  }

  @TestOnly
  public void removeEditor() { forEachTable(GridTable::removeEditor); }

  @Override
  public void setFitColumnsToWidth(boolean fit) {
    // The pinned table keeps its own widths: the columns that fit are the ones the main table shows.
    myMainTable.setAutoResizeMode(fit ? JTable.AUTO_RESIZE_ALL_COLUMNS : JTable.AUTO_RESIZE_OFF);
  }

  /** The main table's slice: the unpinned columns of the view's column model. */
  public TableColumnModel getColumnModel() { return myMainTable.getColumnModel(); }

  public ComponentOrientation getComponentOrientation() { return myMainTable.getComponentOrientation(); }

  /** The main table's width, not the scroll pane's. */
  public int getWidth() { return myMainTable.getWidth(); }

  /** The main table's rows: the grid's rows, or the grid's columns while transposed. */
  public int getRowCount() { return myMainTable.getRowCount(); }

  /** The columns the main table shows, without the pinned ones; {@link #getViewColumnCount} counts the grid's. */
  public int getColumnCount() { return myMainTable.getColumnCount(); }

  /** Takes positions in the view's column model. */
  @TestOnly
  public void moveColumn(int from, int to) { myColumns.getColumnModel().moveColumn(from, to); }

  public int getRowHeight(int row) { return myMainTable.getRowHeight(row); }

  @Override
  public boolean getShowHorizontalLines() { return myMainTable.getShowHorizontalLines(); }

  @Override
  public boolean isStriped() { return myMainTable.isStriped(); }

  /** Takes a main-table column index, as {@link #columnAtPoint} returns. */
  public Rectangle getCellRect(int row, int column, boolean includeSpacing) { return myMainTable.getCellRect(row, column, includeSpacing); }
}
