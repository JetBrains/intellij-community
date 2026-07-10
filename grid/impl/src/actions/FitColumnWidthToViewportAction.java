package com.intellij.database.actions;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridUtil;
import com.intellij.database.run.actions.GridAction;
import com.intellij.database.run.ui.ResultViewWithColumns;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

public class FitColumnWidthToViewportAction extends DumbAwareAction implements GridAction {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DataGrid grid = GridUtil.getDataGrid(e.getDataContext());
    ResultViewWithColumns resultView = grid == null ? null : ObjectUtils.tryCast(grid.getResultView(), ResultViewWithColumns.class);
    e.getPresentation().setEnabledAndVisible(resultView != null && !grid.isEditing());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataGrid grid = GridUtil.getDataGrid(e.getDataContext());
    ResultViewWithColumns resultView = grid == null ? null : ObjectUtils.tryCast(grid.getResultView(), ResultViewWithColumns.class);
    if (resultView != null) resultView.fitColumnsToViewport();
  }
}
