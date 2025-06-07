package com.intellij.database.datagrid;

import com.intellij.database.run.ui.TableResultPanel.ColumnAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.database.run.ui.DataAccessType.DATA_WITH_MUTATIONS;

public class HierarchicalColumnsCollapseManager {
  private final ColumnAttributes myColumnAttributes;
  private final DataGrid myGrid;

  public HierarchicalColumnsCollapseManager(DataGrid grid, ColumnAttributes columnAttributes) {
    myColumnAttributes = columnAttributes;
    myGrid = grid;
  }

  public boolean isColumnCollapsedSubtree(@NotNull ModelIndex<GridColumn> columnIndex) {
    if (!columnIndex.isValid(myGrid)) return false;
    return isColumnCollapsedSubtree(myGrid.getDataModel(DATA_WITH_MUTATIONS).getColumn(columnIndex));
  }

  public boolean isColumnCollapsedSubtree(@Nullable GridColumn column) {
    if (column == null) return false;
    Boolean collapsed = myColumnAttributes.isCollapsedSubtree(column);
    return collapsed != null ? collapsed : false;
  }

  public void setIsCollapsedSubtree(GridColumn column, boolean collapsed) {
    myColumnAttributes.setIsCollapsedSubtree(column, collapsed);
  }

  public boolean isColumnHiddenDueToCollapse(@NotNull ModelIndex<GridColumn> columnIndex) {
    if (!columnIndex.isValid(myGrid)) return false;
    return isColumnHiddenDueToCollapse(myGrid.getDataModel(DATA_WITH_MUTATIONS).getColumn(columnIndex));
  }

  public boolean isColumnHiddenDueToCollapse(@Nullable GridColumn column) {
    if (column == null) return true;
    Boolean hidden = myColumnAttributes.isHiddenDueToCollapse(column);
    return hidden != null ? hidden : false;
  }

  public void setIsHiddenDueToCollapse(GridColumn column, boolean collapsed) {
    myColumnAttributes.setHiddenDueToCollapse(column, collapsed);
  }
}
