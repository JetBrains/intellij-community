package com.intellij.database.run.actions;

import com.intellij.database.DataGridBundle;
import com.intellij.database.datagrid.*;
import com.intellij.database.run.ui.DataAccessType;
import com.intellij.database.run.ui.DataGridRequestPlace;
import com.intellij.database.run.ui.GridDataSupport;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static com.intellij.database.datagrid.GridUtil.getRowsMutator;
import static com.intellij.database.datagrid.GridUtil.showIgnoreUnsubmittedChangesYesNoDialog;

/**
 * @author Gregory.Shrago
 */
public class DeleteRowsAction extends DeleteActionBase {
  @Override
  protected boolean isEnabled(@NotNull DataGrid grid) {
    GridDataSupport support = grid.getDataSupport();
    return !support.isDeletedRows(grid.getSelectionModel().getSelectedRows()) && grid.isEditable();
  }

  @Override
  protected void doDelete(@NotNull DataGrid grid,
                          @NotNull ModelIndex<GridColumn> contextColumn) {
    deleteRows(grid, grid.getSelectionModel().getSelectedRows());
  }

  private static void deleteRows(@NotNull DataGrid grid, @NotNull ModelIndexSet<GridRow> rows) {
    GridMutator.RowsMutator<GridRow, GridColumn> mutator = getRowsMutator(grid);
    if (mutator == null) return;

    boolean areRowsInserted = true;
    for (ModelIndex<GridRow> index : rows.asIterable()) {
      areRowsInserted &= mutator.isInsertedRow(index);
    }
    boolean canDelete = !mutator.hasPendingChanges() ||
                        !mutator.isUpdateImmediately() ||
                        areRowsInserted ||
                        showIgnoreUnsubmittedChangesYesNoDialog(grid);
    if (!canDelete) return;
    GridSelection<GridRow, GridColumn> selection = grid.getSelectionModel().store();
    GridRequestSource source = new GridRequestSource(new DataGridRequestPlace(grid, rows, ModelIndexSet.forColumns(grid)));
    mutator.deleteRows(source, rows);
    source.getActionCallback().doWhenDone(() -> grid.getAutoscrollLocker().runWithLock(() -> grid.getSelectionModel().restore(grid.getSelectionModel().fit(selection))));
  }

  @Override
  protected int itemsCount(@NotNull DataGrid grid) {
    return grid.getSelectionModel().getSelectedRowCount();
  }

  @Override
  protected @NlsSafe @NotNull String getItemName(@NotNull DataGrid grid) {
    GridRow row = grid.getDataModel(DataAccessType.DATA_WITH_MUTATIONS).getRow(grid.getSelectionModel().getSelectedRow());
    return "#" + Objects.requireNonNull(row).getRowNum();
  }

  @Override
  protected @NotNull ActionText text(@NotNull DataGrid grid) {
    return new ActionText(DataGridBundle.message("grid.delete.action.row.item"),
                          DataGridBundle.message("grid.delete.action.row.items"),
                          DataGridBundle.message("grid.delete.selected.row.action.text"),
                          DataGridBundle.message("grid.delete.selected.rows.action.text"),
                          DataGridBundle.message("grid.delete.selected.row.action.confirmation"),
                          DataGridBundle.message("grid.delete.selected.rows.action.confirmation"));
  }

  @Override
  protected boolean isVisible(@Nullable DataGrid grid) {
    return grid != null && grid.isEditable() && grid.isReady();
  }
}
