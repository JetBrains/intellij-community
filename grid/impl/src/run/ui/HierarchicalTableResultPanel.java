package com.intellij.database.run.ui;

import com.intellij.database.DataGridBundle;
import com.intellij.database.connection.throwable.info.ErrorInfo;
import com.intellij.database.connection.throwable.info.SimpleErrorInfo;
import com.intellij.database.datagrid.*;
import com.intellij.database.run.ui.table.FilterStateControllerForNestedTables;
import com.intellij.database.run.ui.table.LocalFilterState;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.components.breadcrumbs.Breadcrumbs;
import com.intellij.ui.components.breadcrumbs.Crumb;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel.HierarchicalGridColumn;


import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.function.BiConsumer;

import com.intellij.database.datagrid.NestedTablesDataGridModel.NestedTableCellCoordinate;

import static com.intellij.database.datagrid.GridUtil.getGridColumnHeaderPopupActions;
import static com.intellij.database.datagrid.GridUtil.getSettings;
import static com.intellij.database.run.ui.DataAccessType.DATA_WITH_MUTATIONS;

public class HierarchicalTableResultPanel extends TableResultPanel implements DataGridWithNestedTables {
  private final Breadcrumbs myNestedTablesBreadcrumbs;
  private List<Crumb> myElementsOfBreadcrumbs;
  private final NestedTablesNavigationErrorPanel myNestedTablesNavigationErrorPanel = new NestedTablesNavigationErrorPanel();
  private final FilterStateControllerForNestedTables myLocalFilterStateManager;
  private final HierarchicalColumnsCollapseManager myHierarchicalColumnsCollapseManager;

  public HierarchicalTableResultPanel(@NotNull Project project,
                                      @NotNull GridDataHookUp<GridRow, GridColumn> dataHookUp,
                                      @NotNull ActionGroup popupActions,
                                      @NotNull BiConsumer<DataGrid, DataGridAppearance> configurator) {
    this(project, dataHookUp, popupActions, null, getGridColumnHeaderPopupActions(), ActionGroup.EMPTY_GROUP, false, configurator);
  }

  public HierarchicalTableResultPanel(@NotNull Project project,
                                      @NotNull GridDataHookUp<GridRow, GridColumn> dataHookUp,
                                      @NotNull ActionGroup popupActions,
                                      @Nullable ActionGroup gutterPopupActions,
                                      @NotNull ActionGroup columnHeaderActions,
                                      @NotNull ActionGroup rowHeaderActions,
                                      boolean useConsoleFonts,
                                      @NotNull BiConsumer<DataGrid, DataGridAppearance> configurator) {
    super(project, dataHookUp, popupActions, gutterPopupActions, columnHeaderActions, rowHeaderActions, useConsoleFonts, configurator);
    myHierarchicalColumnsCollapseManager =
      new HierarchicalColumnsCollapseManager(this, getColumnAttributes());

    var settings = getSettings(this);
    var isEnableLocalFilterByDefault = settings == null || settings.isEnableLocalFilterByDefault();
    myLocalFilterStateManager = new FilterStateControllerForNestedTables(this);
    myLocalFilterStateManager.getActiveFilterState()
      .setEnabled(myLocalFilterStateManager.getActiveFilterState().isEnabled() && isEnableLocalFilterByDefault);

    if (isNestedTablesSupportEnabled(getDataModel())) {
      myNestedTablesBreadcrumbs = new Breadcrumbs();
      getPanel().setSecondBottomComponent(myNestedTablesBreadcrumbs);
      myNestedTablesBreadcrumbs.addMouseListener(new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          onBreadCrumbClick(e.getX(), e.getY());
        }
      });
      myElementsOfBreadcrumbs = new ArrayList<>();
      myElementsOfBreadcrumbs.add(new Crumb.Impl(AllIcons.Nodes.DataTables, " ", null));
      myNestedTablesBreadcrumbs.setCrumbs(myElementsOfBreadcrumbs);
    }
    else {
      myNestedTablesBreadcrumbs = null;
    }
  }


  @Override
  public void rowsRemoved(ModelIndexSet<GridRow> rows) {
    recreateUIElementsRelatedToNestedTableIfNeeded(); // dangerous
    super.rowsRemoved(rows);
  }

  @Override
  public void cellsUpdated(ModelIndexSet<GridRow> rows, ModelIndexSet<GridColumn> columns, @Nullable GridRequestSource.RequestPlace place) {
    recreateUIElementsRelatedToNestedTableIfNeeded();
    super.cellsUpdated(rows, columns, place);
  }


  /**
   * Checks if the UI elements related to the nested table need to be recreated.
   * This may be necessary in the event of data reloading in the grid with nested tables,
   * causing changes in the structure of the nested tables and invalidates the current UI. .
   */
  private void recreateUIElementsRelatedToNestedTableIfNeeded() {
    if (myElementsOfBreadcrumbs == null) return;
    if (!isNestedTablesSupportEnabled(getDataModel())) return;
    GridModelWithNestedTables modelWithNestedTables = (GridModelWithNestedTables) getDataModel();
    List<NestedTableCellCoordinate> pathToSelectedNestedTable = modelWithNestedTables.getPathToSelectedNestedTable();
    if (pathToSelectedNestedTable.size() == myElementsOfBreadcrumbs.size() - 1) return;
    updateBreadCrumbs(pathToSelectedNestedTable);
    updateNestedPageModels(pathToSelectedNestedTable);
    recreateColumns();
  }

  private void recreateColumns() {
    columnsAdded(getDataModel().getColumnIndices());
  }

  private void updateBreadCrumbs(@NotNull List<NestedTableCellCoordinate> pathToSelectedNestedTable) {
    myElementsOfBreadcrumbs.subList(1, myElementsOfBreadcrumbs.size()).clear();
    for (NestedTableCellCoordinate cellCoordinate : pathToSelectedNestedTable) {
      GridColumn column = cellCoordinate.getColumn();
      int rowNum = cellCoordinate.getRowNum();
      @NlsSafe String breadCrumbText = String.format("%s[%d]", column.getName(), rowNum);
      myElementsOfBreadcrumbs.add(new Crumb.Impl(null, breadCrumbText, null));
    }
    myNestedTablesBreadcrumbs.setCrumbs(myElementsOfBreadcrumbs);
  }

  private void updateNestedPageModels(@NotNull List<NestedTableCellCoordinate> pathToSelectedNestedTable) {
    if (getDataHookup().getPageModel() instanceof NestedTableGridPagingModel<GridRow, GridColumn> nestedPaging) {
      nestedPaging.reset();
      for (NestedTableCellCoordinate cellCoordinate : pathToSelectedNestedTable) {
        Object cellValue = cellCoordinate.getColumn().getValue(cellCoordinate.getRow());
        if (!(cellValue instanceof NestedTable newSelectedNestedTable)) break;
        nestedPaging.enterNestedTable(cellCoordinate, newSelectedNestedTable);
      }
    }
  }

  private void appendNestedTableBreadcrumb(@NlsSafe String colName, int rowNum) {
    @NlsSafe String breadCrumbText = String.format("%s[%d]", colName, rowNum);
    myElementsOfBreadcrumbs.add(new Crumb.Impl(null, breadCrumbText, null));
    myNestedTablesBreadcrumbs.setCrumbs(myElementsOfBreadcrumbs);
  }

  @Override
  public boolean onCellClick(@NotNull ModelIndex<GridRow> rowIdx, @NotNull ModelIndex<GridColumn> colIdx) {
    if (tryUpdateGridToShowNestedTable(rowIdx, colIdx)) {
      if (getDataModel().getRowCount() != 0) {
        getResultView().resetScroll();
      }
      return true;
    }

    return false;
  }

  private boolean tryUpdateGridToShowNestedTable(@NotNull ModelIndex<GridRow> rowIdx, @NotNull ModelIndex<GridColumn> colIdx) {
    if (myNestedTablesBreadcrumbs == null) return false;
    if (!isNestedTableSupportEnabled()) return false;

    GridRow row = getDataModel().getRow(rowIdx);
    GridColumn column = getDataModel().getColumn(colIdx);
    if (column == null || row == null) return false;
    Object cellValue = getDataModel().getValueAt(rowIdx, colIdx);
    if (!(cellValue instanceof NestedTable newSelectedNestedTable)) return false;

    if (!isReady()) {
      myNestedTablesNavigationErrorPanel.show(SimpleErrorInfo.create(DataGridBundle.message("grid.load.nested.table.error.loading")));
      return false;
    } else {
      myNestedTablesNavigationErrorPanel.hideIfShown();
    }

    if (!tryLoadNestedTable(newSelectedNestedTable, row, column)) return false;

    recreateColumns();
    appendNestedTableBreadcrumb(column.getName(), row.getRowNum());

    return true;
  }

  private boolean tryLoadNestedTable(@NotNull NestedTable newSelectedNestedTable, @NotNull GridRow row, @NotNull GridColumn column) {
    GridModelWithNestedTables modelWithNestedTables = (GridModelWithNestedTables)getDataModel();
    NestedTableCellCoordinate coordinate = new NestedTableCellCoordinate(row, column);
    myLocalFilterStateManager.enterNestedTable(coordinate, newSelectedNestedTable);
    if (getDataHookup().getSortingModel() instanceof NestedTablesSortingModel<?> sortingModel) {
      sortingModel.enterNestedTable(coordinate, newSelectedNestedTable);
    }

    if (getDataHookup().getLoader() instanceof NestedTablesGridLoader nestedTablesAwareGridLoader) {
      if (nestedTablesAwareGridLoader.isLoadAllowed()) {
        myNestedTablesNavigationErrorPanel.show(SimpleErrorInfo.create(DataGridBundle.message("grid.load.nested.table.error")));
        return false;
      } else {
        myNestedTablesNavigationErrorPanel.hideIfShown();
      }

      modelWithNestedTables.enterNestedTable(coordinate, newSelectedNestedTable);

      if (getDataHookup().getPageModel() instanceof NestedTableGridPagingModel<GridRow, GridColumn> nestedPaging) {
        nestedPaging.enterNestedTable(coordinate, newSelectedNestedTable);
      }

      if (!(newSelectedNestedTable instanceof StaticNestedTable)) {
        nestedTablesAwareGridLoader.enterNestedTable(coordinate, newSelectedNestedTable);
        getDataHookup().getLoader().loadFirstPage(new GridRequestSource(new DataGridRequestPlace(this)));
      }
    }
    else {
      modelWithNestedTables.enterNestedTable(coordinate, newSelectedNestedTable);
      if (getDataHookup().getPageModel() instanceof NestedTableGridPagingModel<GridRow, GridColumn> nestedPaging) {
        nestedPaging.enterNestedTable(coordinate, newSelectedNestedTable);
      }
    }

    return true;
  }

  @Override
  public void onBreadCrumbClick(int x, int y) {
    if (myNestedTablesBreadcrumbs == null) return;
    if (!isNestedTableSupportEnabled()) return;

    Crumb clicked = myNestedTablesBreadcrumbs.getCrumbAt(x, y);
    if (clicked == null) return;

    int idx = myElementsOfBreadcrumbs.indexOf(clicked);
    int depth = myElementsOfBreadcrumbs.size();
    // current table clicked. nothing to do
    if (idx == depth - 1) return;

    if (getDataModel().isUpdatingNow()) {
      String text = clicked.getText();
      myNestedTablesNavigationErrorPanel.show(
        SimpleErrorInfo.create(DataGridBundle.message("grid.load.prev.nested.table.error.loading", text.isBlank() ? "top level" : text)));
      return;
    } else {
      myNestedTablesNavigationErrorPanel.hideIfShown();
    }

    int steps = depth - (idx + 1);
    updateGridToShowPreviousNestedTable(steps);
  }

  private void updateGridToShowPreviousNestedTable(int steps) {
    GridModelWithNestedTables modelWithNestedTables = (GridModelWithNestedTables) getDataModel();
    NestedTableCellCoordinate coordinateToRestoreScroll = modelWithNestedTables.exitNestedTable(steps);
    if (getDataHookup().getPageModel() instanceof NestedTableGridPagingModel<GridRow, GridColumn> nestedPaging) {
      nestedPaging.exitNestedTable(steps);
    }
    if (getDataHookup().getLoader() instanceof NestedTablesGridLoader nestedTablesAwareGridLoader) {
      nestedTablesAwareGridLoader.exitNestedTable(steps);
    }
    myLocalFilterStateManager.exitNestedTable(steps);
    if (getDataHookup().getSortingModel() instanceof NestedTablesSortingModel<?> sortingModel) {
      sortingModel.exitNestedTable(steps);
    }
    recreateColumns();
    removeTail(myElementsOfBreadcrumbs, steps);
    myNestedTablesBreadcrumbs.setCrumbs(myElementsOfBreadcrumbs);
    if (coordinateToRestoreScroll == null) return;
    showCell(coordinateToRestoreScroll.getRowIdx(), coordinateToRestoreScroll.getColumnIdx(this));
  }

  @Override
  public boolean isNestedTableSupportEnabled() {
    return getDataModel() instanceof GridModelWithNestedTables modelWithNestedTables
           && modelWithNestedTables.isNestedTablesSupportEnabled();
  }

  @Override
  public boolean isNestedTableStatic() {
    return isNestedTableSupportEnabled() &&
           ((GridModelWithNestedTables)getDataModel()).getSelectedNestedTable() instanceof StaticNestedTable;
  }

  @Override
  public boolean isTopLevelGrid() {
    GridModel<GridRow, GridColumn> model = getDataModel();
    if (!(model instanceof GridModelWithNestedTables modelWithNestedTables)) return true;
    return modelWithNestedTables.isTopLevelGrid();
  }

  @Override
  public void afterLastRowAdded() {
    super.afterLastRowAdded();
    myNestedTablesNavigationErrorPanel.hideIfShown();
  }

  @Override
  public @NotNull LocalFilterState getLocalFilterState() {
    return myLocalFilterStateManager.getActiveFilterState();
  }

  private static void removeTail(@NotNull List<?> list, int k) {
    list.subList(Math.max(0, list.size() - k), list.size()).clear();
  }

  @Override
  public void setColumnEnabled(@NotNull ModelIndex<GridColumn> columnIdx, boolean state) {
    GridColumn column = getDataModel(DATA_WITH_MUTATIONS).getColumn(columnIdx);
    if (column == null || isColumnEnabled(column) == state) return;

    if (!(column instanceof HierarchicalGridColumn hierarchicalColumn)) {
      updateColumnEnableState(column, state);
      return;
    }

    if (state) {
      updateEnableStateForColumns(getDisabledAncestorColumns(hierarchicalColumn), state);
      updateColumnEnableState(column, state);
      return;
    }

    HierarchicalGridColumn ancestor = hierarchicalColumn.getParent();
    if (hierarchicalColumn.isLeftMostChildOfDirectAncestor() && ancestor != null) {
      updateEnableStateForColumns(ancestor.getLeaves(), state);
    }
    else {
      updateColumnEnableState(column, state);
    }

    getResultView().onColumnHierarchyChanged();
  }

  static boolean isNestedTablesSupportEnabled(GridModel<GridRow, GridColumn> model) {
    return model instanceof GridModelWithNestedTables && ((GridModelWithNestedTables)model).isNestedTablesSupportEnabled();
  }

  @Override
  public @Nullable HierarchicalColumnsCollapseManager getHierarchicalColumnsCollapseManager() {
    return myHierarchicalColumnsCollapseManager;
  }

  private @NotNull List<? extends GridColumn> getDisabledAncestorColumns(@NotNull HierarchicalGridColumn gridColumn) {
    LinkedList<HierarchicalGridColumn> columnsBranchToUpdate = new LinkedList<>();
    HierarchicalGridColumn ancestor = gridColumn.getParent();
    while (ancestor != null) {
      List<HierarchicalGridColumn> leaves = ancestor.getLeaves();
      HierarchicalGridColumn leftMostChild = leaves.get(0);
      if (Boolean.FALSE.equals(getColumnAttributes().isEnabled(leftMostChild))) {
        columnsBranchToUpdate.addFirst(leftMostChild);
        ancestor = ancestor.getParent();
        continue;
      }
      break;
    }

    return columnsBranchToUpdate;
  }

  private void updateEnableStateForColumns(@NotNull List<? extends GridColumn> columns, boolean state) {
    for (GridColumn c : columns) {
      updateColumnEnableState(c, state);
    }
  }

  private void updateColumnEnableState(@NotNull GridColumn c, boolean state) {
    getColumnAttributes().setEnabled(c, state);

    if (myHierarchicalColumnsCollapseManager.isColumnHiddenDueToCollapse(c)) return;

    GridSelection<GridRow, GridColumn> selection = getSelectionModel().store();
    ModelIndex<GridColumn> colIdx = ModelIndex.forColumn(this, c.getColumnNumber());
    storeOrRestoreSelection(colIdx, state, selection);
    getResultView().setColumnEnabled(colIdx, state);
    fireContentChanged(null); // update structure view
    runWithIgnoreSelectionChanges(() -> {
      getSelectionModel().restore(selection);
    });
  }

  class NestedTablesNavigationErrorPanel {
    private boolean hasShown = false;

    public void show(ErrorInfo errorInfo) {
      showError(errorInfo, null);
      hasShown = true;
    }

    public void hideIfShown() {
      if (hasShown) {
        hideErrorPanel();
        hasShown = false;
      }
    }
  }
}
