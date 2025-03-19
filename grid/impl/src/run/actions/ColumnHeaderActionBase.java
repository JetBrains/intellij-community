package com.intellij.database.run.actions;

import com.intellij.database.DatabaseDataKeys;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridColumn;
import com.intellij.database.datagrid.ModelIndex;
import com.intellij.database.datagrid.ModelIndexSet;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

public abstract class ColumnHeaderActionBase extends DumbAwareAction implements GridAction {
  private final boolean invokeOnGrid;

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  protected ColumnHeaderActionBase() {
    this(false);
  }

  protected ColumnHeaderActionBase(boolean invokeOnGrid) {
    this.invokeOnGrid = invokeOnGrid;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DataGrid grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    e.getPresentation().setEnabledAndVisible(grid != null);
    if (grid == null) return;
    update(e, grid, getColumns(grid));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataGrid grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    if (grid != null) {
      actionPerformed(e, grid, getColumns(grid));
    }
  }

  protected void update(AnActionEvent e, @NotNull DataGrid grid, @NotNull ModelIndexSet<GridColumn> columnIdxs) {
    e.getPresentation().setEnabledAndVisible(columnIdxs.size() > 0 && isValid(columnIdxs, grid));
  }

  protected @NotNull ModelIndexSet<GridColumn> getColumns(@NotNull DataGrid grid) {
    ModelIndex<GridColumn> column = grid.getContextColumn();
    return column.value != -1 || !invokeOnGrid ? ModelIndexSet.forColumns(grid, column.value) : grid.getSelectionModel().getSelectedColumns();
  }

  private static boolean isValid(@NotNull ModelIndexSet<GridColumn> idxs, @NotNull DataGrid grid) {
    return idxs.asIterable().find(i -> !i.isValid(grid)) == null;
  }

  protected abstract void actionPerformed(AnActionEvent e, @NotNull DataGrid grid, @NotNull ModelIndexSet<GridColumn> columnIdxs);
}
