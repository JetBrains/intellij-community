package com.intellij.database.run.actions;

import com.intellij.database.DatabaseDataKeys;
import com.intellij.database.datagrid.*;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

import static com.intellij.database.run.actions.AddColumnAction.newInsertOrCloneColumnRequestSource;
import static com.intellij.database.run.actions.DeleteColumnsAction.getColumns;

public class CloneColumnAction extends DumbAwareAction implements GridAction {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DataGrid dataGrid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    boolean singleColumnSelected = dataGrid != null && getColumns(dataGrid).size() == 1;
    boolean canAddColumn = GridUtil.canMutateColumns(e.getData(DatabaseDataKeys.DATA_GRID_KEY));
    e.getPresentation()
      .setEnabledAndVisible(singleColumnSelected && canAddColumn && !GridHelper.get(dataGrid).isModifyColumnAcrossCollection());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataGrid dataGrid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    ModelIndexSet<GridColumn> columns = dataGrid != null ? getColumns(dataGrid) : null;
    if (columns == null || columns.size() != 1) return;
    ModelIndex<GridColumn> selectedColumn = columns.asIterable().first();
    if (selectedColumn != null && selectedColumn.isValid(dataGrid)) {
      cloneColumn(dataGrid, selectedColumn);
    }
  }

  public static void cloneColumn(@NotNull DataGrid grid, @NotNull ModelIndex<GridColumn> selectedColumn) {
    final GridMutator.ColumnsMutator<GridRow, GridColumn> mutator = GridUtil.getColumnsMutator(grid);
    if (mutator == null) return;

    if (mutator.isUpdateImmediately() && mutator.hasPendingChanges()) {
      grid.submit().doWhenDone(() -> mutator.cloneColumn(newInsertOrCloneColumnRequestSource(grid), selectedColumn));
      return;
    }
    mutator.cloneColumn(newInsertOrCloneColumnRequestSource(grid), selectedColumn);
  }
}
