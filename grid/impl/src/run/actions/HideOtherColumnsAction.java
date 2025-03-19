package com.intellij.database.run.actions;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.HierarchicalColumnsDataGridModel.HierarchicalGridColumn;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.datagrid.ModelIndexSet;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.openapi.actionSystem.AnActionEvent;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class HideOtherColumnsAction extends ColumnHeaderActionBase {
  public HideOtherColumnsAction() {
    super(true);
  }

  @Override
  protected void actionPerformed(AnActionEvent e, @NotNull DataGrid grid, @NotNull ModelIndexSet<GridColumn> columnIdxs) {
    for (ModelIndex<GridColumn> index : columnIdxs.asIterable()) {
      grid.setColumnEnabled(index, false);
    }
  }

  @Override
  protected @NotNull ModelIndexSet<GridColumn> getColumns(@NotNull DataGrid grid) {
    ModelIndexSet<GridColumn> selected = super.getColumns(grid);
    return selected.size() > 0 ? getOtherColumns(grid, selected) : selected;
  }

  private static @NotNull ModelIndexSet<GridColumn> getOtherColumns(@NotNull DataGrid grid, @NotNull ModelIndexSet<GridColumn> selected) {
    List<ModelIndex<GridColumn>> selectedList = selected.asList();
    List<ModelIndex<GridColumn>> additionalColumnsToKeep = getAdditionalColumnsToKeep(grid, selectedList);
    List<ModelIndex<GridColumn>> indices = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getColumnIndices().asList();
    IntList other = new IntArrayList();
    for (ModelIndex<GridColumn> idx : indices) {
      if (!selectedList.contains(idx) && !additionalColumnsToKeep.contains(idx)) {
        other.add(idx.value);
      }
    }
    return ModelIndexSet.forColumns(grid, other.toIntArray());
  }

  private static List<ModelIndex<GridColumn>> getAdditionalColumnsToKeep(
    @NotNull DataGrid grid, @NotNull List<ModelIndex<GridColumn>> selectedColumns
  ) {
    List<GridColumn> additionalColumnsToKeep = new ArrayList<>();
    for (ModelIndex<GridColumn> colIdx : selectedColumns) {
      GridColumn column = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getColumn(colIdx);
      if (!(column instanceof HierarchicalGridColumn hierarchicalColumn)) continue;
      additionalColumnsToKeep.addAll(getAncestorsBranchToKeep(hierarchicalColumn));
      if (!hierarchicalColumn.isLeftMostChildOfDirectAncestor()) continue;
      additionalColumnsToKeep.addAll(getLeaves(hierarchicalColumn.getParent()));
    }

    return getColumnIndices(grid, additionalColumnsToKeep);
  }

  private static List<HierarchicalGridColumn> getLeaves(@Nullable HierarchicalGridColumn column) {
    if (column == null) return Collections.emptyList();
    return column.getLeaves();
  }

  private static List<GridColumn> getAncestorsBranchToKeep(
    @NotNull HierarchicalGridColumn hierarchicalColumn
  ) {
    List<GridColumn> ancestorsToKeep = new ArrayList<>();

    HierarchicalGridColumn ancestor = hierarchicalColumn.getParent();
    while (ancestor != null) {
      List<HierarchicalGridColumn> leaves = ancestor.getLeaves();
      HierarchicalGridColumn leftMostChild = leaves.get(0);
      ancestorsToKeep.add(leftMostChild);
      ancestor = ancestor.getParent();
    }

    return ancestorsToKeep;
  }

  private static List<ModelIndex<GridColumn>> getColumnIndices(@NotNull DataGrid grid, @NotNull List<? extends GridColumn> columns) {
    List<ModelIndex<GridColumn>> indices = new ArrayList<>(columns.size());
    for (GridColumn column : columns) {
      indices.add(ModelIndex.forColumn(grid, column.getColumnNumber()));
    }

    return indices;
  }
}