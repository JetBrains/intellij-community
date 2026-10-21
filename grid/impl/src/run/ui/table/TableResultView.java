package com.intellij.database.run.ui.table;

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
import com.intellij.database.datagrid.ViewIndex;
import com.intellij.database.extractors.DisplayType;
import com.intellij.database.run.ui.ColumnMove;
import com.intellij.database.run.ui.ColumnOrderRestorer;
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
import com.intellij.database.run.ui.table.statisticsPanel.StatisticsPanelMode;
import com.intellij.database.run.ui.table.statisticsPanel.StatisticsTableHeader;
import com.intellij.find.FindModel;
import com.intellij.find.SearchSession;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.colors.EditorColorsListener;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.ui.TableUtil;
import com.intellij.ui.hover.TableHoverListener;
import com.intellij.ui.table.AdditionalTableHeader;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.ui.JBUI;
import it.unimi.dsi.fastutil.ints.IntSet;
import kotlin.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.CellRendererPane;
import javax.swing.JComponent;
import javax.swing.JScrollBar;
import javax.swing.ListSelectionModel;
import javax.swing.RowSorter;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import java.awt.Color;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** The complete table view, including any pinned columns. */
public final class TableResultView implements ResultView, ResultViewWithCells, ResultViewWithColumns, ResultViewWithRows,
                                              ResultViewWithFrozenColumns, EditorColorsListener, UISettingsListener {
  private final DataGrid myResultPanel;
  private final GridTable myPrimary;
  private final FrozenColumnsController myFrozenColumnsController;
  private final TableFloatingToolbarController myFloatingToolbar;
  private final TableScrollPane myScrollPane;
  private GridColumnLayout<GridRow, GridColumn> myColumnLayout = new DummyGridColumnLayout();
  private StatisticsTableHeader myStatisticsHeader;
  private StatisticsHeaderHost myStatisticsHeaderHost;
  private Ref<Object> myCommonValue;
  private ModelIndex<GridColumn> myContextColumn;

  public TableResultView(@NotNull DataGrid grid, @NotNull ActionGroup columnActions, @NotNull ActionGroup rowActions) {
    myResultPanel = grid;
    myContextColumn = ModelIndex.forColumn(grid, -1);
    myPrimary = new GridTable(grid, columnActions, rowActions);
    myFloatingToolbar = new TableFloatingToolbarController(myPrimary, grid, grid.getCoroutineScope());
    myFrozenColumnsController = new FrozenColumnsController(myPrimary, grid, columnActions, rowActions,
                                                            this::createHost, myFloatingToolbar);
    myPrimary.installHost(createHost(myPrimary));
    myPrimary.installGridIntegration();
    TableResultViewFactory.configureTable(myPrimary, grid);
    myScrollPane = TableResultViewFactory.createScrollPane(grid, myPrimary, this);
    TableAggregatorWidgetHelper.install(this, grid);
    TableGoToRowHelper.install(this, grid);
    TablePositionWidgetHelper.install(this, grid);
    myFrozenColumnsController.installColumnResizeHandle();
    grid.addDataGridListener(new DataGridListener() {
      @Override
      public void onValueEdited(DataGrid dataGrid, @Nullable Object object) {
        myCommonValue = Ref.create(object);
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

  private GridTableHost createHost(GridTable source) {
    return new TableHost(source);
  }

  private @Nullable GridTable getFrozenView() {
    return myFrozenColumnsController.getFrozenView();
  }

  private void forEachTable(Consumer<GridTable> operation) {
    operation.accept(myPrimary);
    GridTable frozen = getFrozenView();
    if (frozen != null) operation.accept(frozen);
  }

  @Override
  public @NotNull JComponent getComponent() { return myPrimary; }

  /** The scroll pane that holds both tables. {@link TableResultViewFactory#wrap} places it in the grid layout. */
  @NotNull TableScrollPane getScrollPane() { return myScrollPane; }

  @Override
  @RequiresEdt
  public @NotNull Predicate<Component> captureCellComponentMatcher() {
    Component primary = myPrimary;
    Component frozen = getFrozenView();
    return component -> component != null && (component == primary || component == frozen);
  }

  @Override
  public void registerEscapeAction(@NotNull AbstractAction action) {
    forEachTable(table -> table.registerEscapeAction(action));
  }

  @Override
  public boolean isEditing() { return editingTable() != null; }

  private @Nullable GridTable editingTable() {
    if (myPrimary.isEditing()) return myPrimary;
    GridTable frozen = getFrozenView();
    return frozen != null && frozen.isEditing() ? frozen : null;
  }

  public @Nullable Component getEditorComponent() {
    GridTable editing = editingTable();
    return editing == null ? null : editing.getEditorComponent();
  }

  @Override
  public @Nullable TableCellEditor getCellEditor() {
    GridTable editing = editingTable();
    return editing == null ? null : editing.getCellEditor();
  }

  @Override
  public void setFrozenColumns(@NotNull Set<ModelIndex<GridColumn>> columns) {
    if (isEditing() && !stopEditing()) cancelEditing();
    myFrozenColumnsController.setFrozenColumns(columns);
  }

  @Override
  public void showRowNumbers(boolean show) { myFrozenColumnsController.showRowNumbers(show); }

  @Override
  public void dispose() {
    setStatisticsHeader(null);
    Disposer.dispose(myFrozenColumnsController);
    Disposer.dispose(myFloatingToolbar);
    Disposer.dispose(myPrimary);
    myCommonValue = null;
  }

  public int getAvailableColumnsWidth() { return myFrozenColumnsController.getAvailableColumnsWidth(); }

  @Override
  public @NotNull ModelIndex<GridColumn> getContextColumn() { return myContextColumn; }

  @TestOnly
  public void setContextColumn(@NotNull ModelIndex<GridColumn> column) { myContextColumn = column; }

  @TestOnly
  GridTable getPrimaryTable() { return myPrimary; }

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

  public void changeSelection(int rowIndex, int columnIndex, boolean toggle, boolean extend) {
    myPrimary.changeSelection(rowIndex, columnIndex, toggle, extend);
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
    myPrimary.setValueAt(v, row, column, allowImmediateUpdate, source);
  }

  @Override
  public void setValueAt(@Nullable Object v,
                         @NotNull ModelIndex<GridRow> row,
                         @NotNull ModelIndex<GridColumn> column,
                         boolean allowImmediateUpdate,
                         @NotNull GridRequestSource source,
                         @Nullable Object metadata) {
    myPrimary.setValueAt(v, row, column, allowImmediateUpdate, source, metadata);
  }

  @Override
  public void setTransposed(boolean transposed) {
    myPrimary.setTransposed(transposed);
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
    myPrimary.growSelection();
  }

  @Override
  public void shrinkSelection() {
    myPrimary.shrinkSelection();
  }

  @Override
  public void addSelectionChangedListener(@NotNull Consumer<Boolean> listener) {
    myPrimary.addSelectionChangedListener(listener);
  }

  @Override
  public void restoreColumnsOrder(Map<Integer, ModelIndex<GridColumn>> expectedToModel) {
    myPrimary.restoreColumnsOrder(expectedToModel);
  }

  public @NotNull ModelIndex<GridColumn> uiColumn(int uiColumn) {
    return myPrimary.uiColumn(uiColumn);
  }

  public int fromRealRowIdx(int uiRow) {
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

  public @Nullable JComponent getCellRendererComponent(@NotNull ViewIndex<GridRow> viewRow,
                                                       @NotNull ViewIndex<GridColumn> viewColumn,
                                                       boolean forDisplay) {
    return myPrimary.getCellRendererComponent(viewRow, viewColumn, forDisplay);
  }

  @Override
  public void setColumnEnabled(@NotNull ModelIndex<GridColumn> columnIdx, boolean state) {
    myPrimary.setColumnEnabled(columnIdx, state);
  }

  @Override
  public void setColumnsEnabled(@NotNull List<ModelIndex<GridColumn>> columns, boolean state) {
    myPrimary.setColumnsEnabled(columns, state);
  }

  @Override
  public void setRowEnabled(@NotNull ModelIndex<GridRow> rowIdx, boolean state) {
    myPrimary.setRowEnabled(rowIdx, state);
  }

  public void startPaintingSession() {
    forEachTable(table -> table.startPaintingSession());
  }

  public void endPaintingSession() {
    forEachTable(table -> table.endPaintingSession());
  }

  @Override
  public void updateSortKeysFromColumnAttributes() {
    forEachTable(table -> table.updateSortKeysFromColumnAttributes());
  }

  @Override
  public void orderingAndVisibilityChanged() {
    myPrimary.orderingAndVisibilityChanged();
  }

  @Override
  public @NotNull RawIndexConverter getRawIndexConverter() {
    return myPrimary.getRawIndexConverter();
  }

  @Override
  public void addMouseListenerToComponents(@NotNull MouseListener listener) {
    myPrimary.addMouseListenerToComponents(listener);
    myFrozenColumnsController.addMouseListenerToComponents(listener);
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

  public int rowAtPoint(@NotNull Point point) {
    return myPrimary.rowAtPoint(point);
  }

  @Override
  public boolean isViewModified() {
    return myPrimary.isViewModified();
  }

  @Override
  public void contentLanguageUpdated(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull Language language) {
    forEachTable(table -> table.contentLanguageUpdated(columnIdx, language));
  }

  @Override
  public void displayTypeUpdated(@NotNull ModelIndex<GridColumn> columnIdx, @NotNull DisplayType displayType) {
    forEachTable(table -> table.displayTypeUpdated(columnIdx, displayType));
  }

  public void setRowHeight(int row, int rowHeight) {
    myPrimary.setRowHeight(row, rowHeight);
  }

  @Override
  public void columnsAdded(ModelIndexSet<GridColumn> columnIndices) {
    myPrimary.columnsAdded(columnIndices);
  }

  @Override
  public void columnAttributesUpdated() {
    myPrimary.columnAttributesUpdated();
  }

  @Override
  public void columnsRemoved(ModelIndexSet<GridColumn> columns) {
    myPrimary.columnsRemoved(columns);
  }

  @Override
  public void rowsAdded(ModelIndexSet<GridRow> rows) {
    myPrimary.rowsAdded(rows);
  }

  @Override
  public void afterLastRowAdded() {
    myPrimary.afterLastRowAdded();
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
  }

  @Override
  public void cellsUpdated(ModelIndexSet<GridRow> rows, ModelIndexSet<GridColumn> columns, @Nullable GridRequestSource.RequestPlace place) {
    myPrimary.cellsUpdated(rows, columns, place);
  }

  @Override
  public void changeSelectedColumnsWidth(int delta) {
    myPrimary.changeSelectedColumnsWidth(delta);
  }

  @Override
  public void fitColumnsToViewport() {
    List<ResultViewColumn> columns = renderedColumns();
    int width = getAvailableColumnsWidth();
    if (columns.isEmpty() || width <= 0) return;
    int columnWidth = Math.max(JBUI.scale(40), width / columns.size());
    for (ResultViewColumn column : columns) column.setColumnWidthByUser(columnWidth);
  }

  @Override
  public void scrollColumnsIntoView(@NotNull List<ModelIndex<GridColumn>> columns) {
    myPrimary.scrollColumnsIntoView(columns);
  }

  public @NotNull IntSet columnsThatCanBePinned(@NotNull Set<ModelIndex<GridColumn>> pinnedColumns) {
    return myPrimary.columnsThatCanBePinned(pinnedColumns);
  }

  @Override
  public boolean canFitPinnedColumns(@NotNull Set<ModelIndex<GridColumn>> pinnedColumns) {
    return myPrimary.canFitPinnedColumns(pinnedColumns);
  }

  @Override
  public void resetColumnWidths() {
    forEachTable(table -> table.resetColumnWidths());
  }

  @Override
  public void resetRowHeights() {
    forEachTable(table -> table.resetRowHeights());
  }

  public @NotNull LocalFilterState getLocalFilterState() {
    return myPrimary.getLocalFilterState();
  }

  public void updateRowFilter() {
    forEachTable(table -> table.updateRowFilter());
  }

  @Override
  public @Nullable Color getCellBackground(@NotNull ViewIndex<GridRow> row, @NotNull ViewIndex<GridColumn> column, boolean selected) {
    return myPrimary.getCellBackground(row, column, selected);
  }

  @Override
  public @NotNull Color getCellForeground(boolean selected) {
    return myPrimary.getCellForeground(selected);
  }

  @Override
  public void reinitSettings() {
    forEachTable(table -> table.reinitSettings());
  }

  public boolean isCellEditable(int row, int column) {
    return myPrimary.isCellEditable(row, column);
  }

  public boolean editCellAt(int row, int column, EventObject e) {
    return myPrimary.editCellAt(row, column, e);
  }

  @Override
  public void createDefaultColumnsFromModel() {
    myPrimary.createDefaultColumnsFromModel();
  }

  public Font getFont() {
    return myPrimary.getFont();
  }

  @Override
  public void globalSchemeChange(@Nullable EditorColorsScheme scheme) {
    forEachTable(table -> table.globalSchemeChange(scheme));
  }

  public void changeFontSize(int increment, double scale) {
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

  public Color getForeground() {
    return myPrimary.getForeground();
  }

  public Color getBackground() {
    return myPrimary.getBackground();
  }

  public void setBackground(@NotNull Color bg) {
    myPrimary.setBackground(bg);
  }

  public Color getGridColor() {
    return myPrimary.getGridColor();
  }

  public void scrollRectToVisible(@NotNull Rectangle rect) {
    myPrimary.scrollRectToVisible(rect);
  }

  public GridTableModel getModel() {
    return myPrimary.getModel();
  }

  public Object getValueAt(int row, int column) {
    return myPrimary.getValueAt(row, column);
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
    return myPrimary.getLayoutColumn(column);
  }

  @Override
  public @Nullable ResultViewColumn getColumnForPersistence(@NotNull ModelIndex<?> column) {
    ResultViewColumn local = myPrimary.getLayoutColumn(column);
    GridTable frozen = getFrozenView();
    if (local == null || !local.isFrozenHidden() || frozen == null) return local;
    int index = frozen.viewColumnOf(column.value);
    return index < 0 ? local : frozen.localColumnAt(index);
  }

  @Override
  public @Nullable ResultViewColumn getLayoutColumnForDataColumn(@NotNull ModelIndex<GridColumn> column) {
    return myPrimary.getLayoutColumnForDataColumn(column);
  }

  @Override
  public @NotNull ModelIndexSet<GridRow> getVisibleRows() {
    return myPrimary.getVisibleRows();
  }

  @Override
  public @NotNull ModelIndexSet<GridColumn> getVisibleColumns() {
    return myPrimary.getVisibleColumns();
  }

  @Override
  public int getViewColumnCount() {
    return myPrimary.getViewColumnCount();
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
    return myPrimary.isCellEditingAllowed();
  }

  @Override
  public void editSelectedCell() {
    int row = myPrimary.getSelectionModel().getLeadSelectionIndex();
    int column = myPrimary.getColumnModel().getSelectionModel().getLeadSelectionIndex();
    if (row < 0 || column < 0) return;
    GridTable target = renderingTable(column);
    int targetColumn = target == myPrimary ? column : convertColumn(myPrimary, column, target);
    if (targetColumn >= 0) TableUtil.editCellAt(target, row, targetColumn);
  }

  @Override
  public void editSelectedCellWithValue(Object value) {
    editSelectedCellWithValue(value, true);
  }

  public void editSelectedCellWithValue(Object value, boolean shouldMoveFocus) {
    int column = myPrimary.getColumnModel().getSelectionModel().getLeadSelectionIndex();
    if (column < 0) return;
    GridTable target = renderingTable(column);
    target.withCurrentValue(value, shouldMoveFocus, this::editSelectedCell);
  }

  @Override
  public boolean isMultiEditingAllowed() {
    return myPrimary.isMultiEditingAllowed();
  }

  public TableCellRenderer getCellRenderer(int row, int column) {
    return myPrimary.getCellRenderer(row, column);
  }

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
    myPrimary.setStatisticsHeader(statisticsHeader);
    if (statisticsHeader == null) {
      myStatisticsHeaderHost = null;
    }
    else {
      myStatisticsHeaderHost = new StatisticsHeaderHost(myPrimary, this, statisticsHeader);
    }
  }

  public StatisticsTableHeader getStatisticsHeader() {
    return myPrimary.getStatisticsHeader();
  }

  public void setStatisticsPanelMode(StatisticsPanelMode newPanelMode) {
    myPrimary.setStatisticsPanelMode(newPanelMode);
  }

  public StatisticsPanelMode getStatisticsPanelMode() {
    return myPrimary.getStatisticsPanelMode();
  }

  @Override
  public void onLocalFilterStateChanged() {
    updateRowFilter();
  }


  private static int convertColumn(GridTable source, int column, GridTable target) {
    int modelColumn = source.getRawIndexConverter().column2Model().applyAsInt(column);
    return modelColumn < 0 ? -1 : target.viewColumnOf(modelColumn);
  }

  GridTable primaryTable() { return myPrimary; }

  private GridTable renderingTable(int primaryColumn) {
    GridTable frozen = getFrozenView();
    return frozen != null && myPrimary.isColumnFrozenHidden(primaryColumn) ? frozen : myPrimary;
  }

  private ResultViewColumn renderedColumnAt(int primaryColumn) {
    GridTable target = renderingTable(primaryColumn);
    int column = target == myPrimary ? primaryColumn : convertColumn(myPrimary, primaryColumn, target);
    return target.localColumnAt(column);
  }

  private List<ResultViewColumn> renderedColumns() {
    List<ResultViewColumn> columns = new ArrayList<>();
    for (int i = 0; i < myPrimary.getColumnCount(); i++) {
      ResultViewColumn column = renderedColumnAt(i);
      if (column != null) columns.add(column);
    }
    return columns;
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
    int primaryColumn = source == myPrimary ? column : convertColumn(source, column, myPrimary);
    if (primaryColumn < 0) return;
    if (interval) {
      int lead = myPrimary.getColumnModel().getSelectionModel().getLeadSelectionIndex();
      if (lead < 0) lead = primaryColumn;
      if (exclusive) {
        selection.addRowSelectionInterval(myPrimary.getRowCount() - 1, 0);
        selection.addColumnSelectionInterval(primaryColumn, lead);
      }
      else {
        selection.setRowSelectionInterval(myPrimary.getRowCount() - 1, 0);
        selection.setColumnSelectionInterval(primaryColumn, lead);
      }
    }
    else if (exclusive) {
      if (myPrimary.isColumnSelected(primaryColumn)) {
        myPrimary.removeColumnSelectionInterval(primaryColumn, primaryColumn);
      }
      else {
        selection.addRowSelectionInterval(myPrimary.getRowCount() - 1, 0);
        selection.addColumnSelectionInterval(primaryColumn, primaryColumn);
      }
    }
    else {
      selection.setRowSelectionInterval(myPrimary.getRowCount() - 1, 0);
      selection.setColumnSelectionInterval(primaryColumn, primaryColumn);
    }
  }

  private final class TableHost implements GridTableHost {
    private final GridTable myTable;

    private TableHost(GridTable table) { myTable = table; }

    @Override
    public boolean isPrimary() { return myTable == myPrimary; }

    @Override
    public boolean applyColumnsDisplayOrder() {
      return isPrimary() && myResultPanel instanceof ColumnOrderRestorer owner && owner.applyColumnsDisplayOrder(TableResultView.this);
    }

    @Override
    public void columnMoved(@NotNull ColumnMove move) {
      if (isPrimary() && myResultPanel instanceof ColumnOrderRestorer owner) owner.columnMovedInView(TableResultView.this, move);
    }

    @Override
    public void dropFrozenColumns() { if (isPrimary()) setFrozenColumns(Set.of()); }

    @Override
    public void onTransposed() { if (isPrimary()) myFrozenColumnsController.refreshRowNumbers(); }

    @Override
    public @NotNull GridRowHeader createRowHeader() {
      GridRowHeader header = myResultPanel.createRowHeader(TableResultView.this);
      header.updatePreferredSize();
      return header;
    }

    @Override
    public boolean stopEditingBeforeSelectionChange(int row, int column) {
      return myFrozenColumnsController.stopEditingBeforeSelectionChange(myTable, row, column);
    }

    @Override
    public boolean handleCellRangeSelection(int row, int column, boolean toggle) {
      return myFrozenColumnsController.handleCellRangeSelection(myTable, row, column, toggle);
    }

    @Override
    public void afterChangeSelection(int column, boolean toggle, boolean extend) {
      myFrozenColumnsController.afterChangeSelection(myTable, column, toggle, extend);
    }

    @Override
    public void processMouseEvent(@NotNull MouseEvent event, @NotNull Runnable localHandler) {
      myFrozenColumnsController.processMouseEvent(myTable, event, localHandler);
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
    public int adjustColumnMoveTarget(int target) { return myFrozenColumnsController.adjustColumnMoveTarget(target); }

    @Override
    public int constrainFrozenColumnResize(@NotNull TableResultViewColumn column, int width) {
      return myFrozenColumnsController.constrainFrozenColumnResize(myTable, column, width);
    }

    @Override
    public void columnMarginChanged() { myFrozenColumnsController.columnMarginChanged(myTable); }

    @Override
    public void syncRowHeight(int row, int height) { myFrozenColumnsController.syncRowHeight(myTable, row, height); }

    @Override
    public void changeFontSize(int increment, double scale) { TableResultView.this.changeFontSize(increment, scale); }

    @Override
    public int constrainSelectedColumnWidthDelta(int @NotNull [] columns, int delta) {
      return myFrozenColumnsController.constrainSelectedColumnWidthDelta(myTable, columns, delta);
    }

    @Override
    public ResultViewColumn renderedColumnAt(int column) {
      return isPrimary() ? TableResultView.this.renderedColumnAt(column) : myTable.localColumnAt(column);
    }

    @Override
    public int toGridViewColumn(int column) { return isPrimary() ? column : convertColumn(myTable, column, myPrimary); }

    @Override
    public boolean stopEditingBeforeEdit() {
      GridTable other = isPrimary() ? getFrozenView() : myPrimary;
      TableCellEditor editor = other == null ? null : other.getCellEditor();
      return editor == null || editor.stopCellEditing();
    }

    @Override
    public Ref<Object> getCommonValue() { return myCommonValue; }

    @Override
    public void clearCommonValue() { myCommonValue = null; }

    @Override
    public @NotNull ModelIndex<GridColumn> getContextColumn() { return myContextColumn; }

    @Override
    public void setContextColumn(@NotNull ModelIndex<GridColumn> column) { myContextColumn = column; }

    @Override
    public boolean isColumnSelected(int column) { return myPrimary.isColumnSelected(toGridViewColumn(column)); }

    @Override
    public void selectHeaderColumn(int column, @NotNull MouseEvent event) {
      TableResultView.this.selectHeaderColumn(myTable, column, event);
    }

    @Override
    public int getAvailableColumnsWidth() { return TableResultView.this.getAvailableColumnsWidth(); }

    @Override
    public ResultViewColumn getColumnForPersistence(@NotNull ModelIndex<?> column) {
      return TableResultView.this.getColumnForPersistence(column);
    }

    @Override
    public void editSelectedCell() { TableResultView.this.editSelectedCell(); }

    @Override
    public void editSelectedCellWithValue(Object value, boolean shouldMoveFocus) {
      TableResultView.this.editSelectedCellWithValue(value, shouldMoveFocus);
    }

    @Override
    public void fitColumnsToViewport() { TableResultView.this.fitColumnsToViewport(); }

    @Override
    public int @NotNull [] getSelectedRows() { return myPrimary.getSelectedRows(); }

    @Override
    public int @NotNull [] getSelectedColumns() { return myPrimary.getSelectedColumns(); }

    @Override
    public int columnToModel(int column) { return myPrimary.convertColumnIndexToModel(column); }

    @Override
    public int rowToModel(int row) { return myPrimary.convertRowIndexToModel(row); }
  }

  private final class TableColumnLayoutContext implements ColumnLayoutContext {
    @Override
    public boolean isTransposed() { return TableResultView.this.isTransposed(); }

    @Override
    public ResultViewColumn getLayoutColumn(@NotNull ModelIndex<?> column) { return getColumnForPersistence(column); }

    @Override
    public int getHeaderWidth(@NotNull ResultViewColumn column) {
      int primaryColumn = myPrimary.convertColumnIndexToView(column.getModelIndex());
      GridTable table = renderingTable(primaryColumn);
      TableCellRenderer renderer = table.getTableHeader().getDefaultRenderer();
      Component component = renderer instanceof GridTable.MyCellRenderer header
                            ? header.getHeaderCellRendererComponent(column.getModelIndex(), false)
                            : renderer.getTableCellRendererComponent(table, column.getHeaderValue(), false, false, -1,
                                                                     table.convertColumnIndexToView(column.getModelIndex()));
      CellRendererPane pane = new CellRendererPane();
      pane.add(component);
      component.validate();
      int width = component.getPreferredSize().width + column.getAdditionalWidth();
      pane.removeAll();
      return width;
    }

    @Override
    public int getCellPreferredWidth(@NotNull ViewIndex<GridRow> row, @NotNull ViewIndex<GridColumn> column) {
      JComponent component = myPrimary.getCellRendererComponent(row, column, false);
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

  public void setStatisticsHeaderPosition(AdditionalTableHeader.Position position) {
    if (myStatisticsHeaderHost != null) myStatisticsHeaderHost.setPosition(position);
  }

  public void setColumnHeaderPainter(@Nullable ColumnHeaderPainter painter) {
    forEachTable(table -> table.setColumnHeaderPainter(painter));
  }

  public void addColumnHeaderMouseListener(@NotNull MouseListener listener) {
    myPrimary.getTableHeader().addMouseListener(listener);
  }

  public int getColumnHeaderHeight() { return myPrimary.getTableHeader().getHeight(); }

  public @Nullable TableColumn getDraggedColumn() { return myPrimary.getTableHeader().getDraggedColumn(); }

  public int getHeaderDraggedDistance() { return myPrimary.getTableHeader().getDraggedDistance(); }

  public void refreshCells() { forEachTable(table -> table.tableChanged(null)); }

  public int columnAtPoint(@NotNull Point point) { return myPrimary.columnAtPoint(point); }

  public int getHoveredRow() {
    GridTable frozen = getFrozenView();
    int row = frozen == null ? -1 : TableHoverListener.getHoveredRow(frozen);
    return row >= 0 ? row : TableHoverListener.getHoveredRow(myPrimary);
  }

  public void removeEditor() { forEachTable(GridTable::removeEditor); }

  public void setAutoResizeMode(int mode) { myPrimary.setAutoResizeMode(mode); }

  public TableColumnModel getColumnModel() { return myPrimary.getColumnModel(); }

  public ComponentOrientation getComponentOrientation() { return myPrimary.getComponentOrientation(); }

  public int getWidth() { return myPrimary.getWidth(); }

  public int getHeight() { return myPrimary.getHeight(); }

  public int getRowCount() { return myPrimary.getRowCount(); }

  public int getColumnCount() { return myPrimary.getColumnCount(); }

  public int[] getSelectedRows() { return myPrimary.getSelectedRows(); }

  public int[] getSelectedColumns() { return myPrimary.getSelectedColumns(); }

  public int getSelectedRow() { return myPrimary.getSelectedRow(); }

  public int getSelectedColumn() { return myPrimary.getSelectedColumn(); }

  public int getSelectedRowCount() { return myPrimary.getSelectedRowCount(); }

  public int getSelectedColumnCount() { return myPrimary.getSelectedColumnCount(); }

  public ListSelectionModel getSelectionModel() { return myPrimary.getSelectionModel(); }

  public RowSorter<? extends TableModel> getRowSorter() { return myPrimary.getRowSorter(); }

  public void moveColumn(int from, int to) { myPrimary.moveColumn(from, to); }

  public void setRowSelectionInterval(int from, int to) { myPrimary.setRowSelectionInterval(from, to); }

  public void setColumnSelectionInterval(int from, int to) { myPrimary.setColumnSelectionInterval(from, to); }

  public void clearSelection() { myPrimary.clearSelection(); }

  public int convertRowIndexToModel(int row) { return myPrimary.convertRowIndexToModel(row); }

  public int convertRowIndexToView(int row) { return myPrimary.convertRowIndexToView(row); }

  public int convertColumnIndexToModel(int column) { return myPrimary.convertColumnIndexToModel(column); }

  public int convertColumnIndexToView(int column) { return myPrimary.convertColumnIndexToView(column); }

  public boolean editCellAt(int row, int column) { return editCellAt(row, column, null); }

  public int getRowHeight(int row) { return myPrimary.getRowHeight(row); }

  @Override
  public boolean getShowHorizontalLines() { return myPrimary.getShowHorizontalLines(); }

  @Override
  public boolean isStriped() { return myPrimary.isStriped(); }

  public Rectangle getCellRect(int row, int column, boolean includeSpacing) { return myPrimary.getCellRect(row, column, includeSpacing); }
}
