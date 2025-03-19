package com.intellij.database.actions;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

public class ResetColumnsWidth extends DumbAwareAction {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DataGrid dataGrid = GridUtil.getDataGrid(e.getDataContext());
    e.getPresentation().setEnabledAndVisible(dataGrid != null);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataGrid dataGrid = GridUtil.getDataGrid(e.getDataContext());
    if (dataGrid != null) dataGrid.resetLayout();
  }
}
