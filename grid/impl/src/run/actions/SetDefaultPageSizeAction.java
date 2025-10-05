package com.intellij.database.run.actions;

import com.intellij.database.DataGridBundle;
import com.intellij.database.DatabaseDataKeys;
import com.intellij.database.datagrid.*;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

public class SetDefaultPageSizeAction extends DumbAwareAction {

  public SetDefaultPageSizeAction() {
    super(DataGridBundle.messagePointer("action.SetDefaultPageSize.text"));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DataGrid grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    if (grid == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    e.getPresentation().setEnabledAndVisible(true);
    e.getPresentation().setText(DataGridBundle.message("action.SetDefaultPageSize.text"));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataGrid grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    if (grid == null) return;

    int pageSize = grid.getDataHookup().getPageModel().getPageSize();
    GridHelper helper = GridHelper.get(grid);

    helper.setDefaultPageSize(pageSize);
    helper.setLimitDefaultPageSize(!GridUtilCore.isPageSizeUnlimited(pageSize));
  }
}
