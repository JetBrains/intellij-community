package com.intellij.database.run.actions;

import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.*;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.database.run.ui.DataGridRequestPlace;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class DeleteColumnsAction extends DeleteActionBase {
  @Override
  protected boolean isVisible(@Nullable DataGrid grid) {
    return GridUtil.canMutateColumns(grid);
  }

  @Override
  protected boolean isEnabled(@NotNull DataGrid grid) {
    return GridUtil.canMutateColumns(grid) && getColumns(grid).size() != 0;
  }

  public static @NotNull ModelIndexSet<GridColumn> getColumns(@NotNull DataGrid grid) {
    ModelIndex<GridColumn> column = grid.getContextColumn();
    return column.value != -1 ? ModelIndexSet.forColumns(grid, column.value) : grid.getSelectionModel().getSelectedColumns();
  }

  @Override
  protected void doDelete(@NotNull DataGrid grid, @NotNull ModelIndex<GridColumn> contextColumn) {
    deleteColumns(grid, contextColumn.value != -1 ? ModelIndexSet.forColumns(grid, contextColumn.value) : grid.getSelectionModel().getSelectedColumns());
  }

  public static void deleteColumns(@NotNull DataGrid grid, @NotNull ModelIndexSet<GridColumn> columns) {
    GridMutator.ColumnsMutator<GridRow, GridColumn> mutator = GridUtil.getColumnsMutator(grid);
    if (mutator != null) {
      GridSelection<GridRow, GridColumn> selection = grid.getSelectionModel().store();
      GridRequestSource source = new GridRequestSource(new DataGridRequestPlace(grid, ModelIndexSet.forRows(grid), columns));
      mutator.deleteColumns(source, columns);
      source.getActionCallback().doWhenDone(() -> grid.getAutoscrollLocker().runWithLock(() -> grid.getSelectionModel().restore(grid.getSelectionModel().fit(selection))));
    }
  }

  @Override
  protected int itemsCount(@NotNull DataGrid grid) {
    ModelIndex<GridColumn> contextColumn = grid.getContextColumn();
    return contextColumn.asInteger() != -1 ? 1 : grid.getSelectionModel().getSelectedColumnCount();
  }

  @Override
  protected @NlsSafe @NotNull String getItemName(@NotNull DataGrid grid) {
    ModelIndex<GridColumn> column = grid.getContextColumn();
    if (column.asInteger() == -1) column = grid.getSelectionModel().getSelectedColumn();
    return Objects.requireNonNull(grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getColumn(column)).getName();
  }

  @Override
  protected @NotNull ActionText text(@NotNull DataGrid grid) {
    int acrossCollection = GridHelper.get(grid).isModifyColumnAcrossCollection() ? 1 : 0;

    //noinspection DialogTitleCapitalization
    return new ActionText(DataGridBundle.message("grid.delete.column.action.text", acrossCollection),
                          DataGridBundle.message("grid.delete.columns.action.text", acrossCollection),
                          DataGridBundle.message("grid.delete.selected.column.action.text", acrossCollection),
                          DataGridBundle.message("grid.delete.selected.columns.action.text", acrossCollection),
                          DataGridBundle.message("grid.delete.selected.column.action.confirmation"),
                          DataGridBundle.message("grid.delete.selected.columns.action.confirmation"));
  }
}
