package com.intellij.database.actions;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridUtil;
import com.intellij.database.run.actions.GridAction;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

public class SelectColumnAction extends DumbAwareAction implements GridAction {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DataGrid grid = GridUtil.getDataGrid(e.getDataContext());
    e.getPresentation().setEnabledAndVisible(grid != null && grid.getVisibleRows().size() > 0 &&
                                             grid.getContextColumn().asInteger() != -1);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataGrid grid = GridUtil.getDataGrid(e.getDataContext());
    if (grid == null) return;
    grid.getSelectionModel().setColumnSelection(grid.getContextColumn(), false);
    grid.getSelectionModel().selectWholeColumn();
  }
}
