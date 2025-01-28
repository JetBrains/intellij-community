package com.intellij.database.run.actions;

import com.intellij.database.datagrid.*;
import com.intellij.database.run.ui.GridDataSupport;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

import static com.intellij.database.datagrid.GridUtil.hideEditActions;

public class RevertMutations extends DumbAwareAction implements GridAction {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DataGrid grid = GridUtil.getDataGrid(e.getDataContext());
    if (grid == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    GridDataSupport support = grid.getDataSupport();
    boolean hasChanges = support.hasPendingChanges() || support.getInsertedColumnsCount() > 0;
    boolean visible = (!support.isSubmitImmediately() || hasChanges) && !grid.getDataHookup().isReadOnly() && !hideEditActions(grid, e.getPlace());
    boolean enabled = visible && hasChanges && support.canRevert() && hasChangeUnderSelection(grid);
    e.getPresentation().setVisible(visible);
    e.getPresentation().setEnabled(enabled);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataGrid grid = GridUtil.getDataGrid(e.getDataContext());
    if (grid == null || !grid.getDataSupport().hasRowMutator()) return;
    GridDataSupport support = grid.getDataSupport();
    ModelIndexSet<GridColumn> columns = grid.getSelectionModel().getSelectedColumns();
    ModelIndexSet<GridRow> rows = grid.getSelectionModel().getSelectedRows();
    if (columns.size() != 0 && rows.size() != 0) {
      support.revert(rows, columns);
    }
  }

  private static boolean hasChangeUnderSelection(@NotNull DataGrid grid) {
    SelectionModel<GridRow, GridColumn> model = grid.getSelectionModel();
    ModelIndexSet<GridRow> rows = model.getSelectedRows();
    ModelIndexSet<GridColumn> columns = model.getSelectedColumns();
    GridDataSupport support = grid.getDataSupport();
    if (columns.asIterable().find(column -> support.isDeletedColumn(column) || support.isInsertedColumn(column)) != null) return true;
    for (ModelIndex<GridRow> rowIdx : rows.asIterable()) {
      ModelIndexSet<GridRow> rowIdxSet = ModelIndexSet.forRows(grid, rowIdx.asInteger());
      if (support.isDeletedRows(rowIdxSet) || GridUtil.isInsertedRow(grid, rowIdx)) return true;
      for (ModelIndex<GridColumn> columnIdx : columns.asIterable()) {
        if (support.isModified(rowIdx, columnIdx)) return true;
      }
    }
    return false;
  }
}
