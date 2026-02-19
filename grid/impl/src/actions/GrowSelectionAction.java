package com.intellij.database.actions;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridUtil;
import com.intellij.database.run.actions.GridAction;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class GrowSelectionAction extends DumbAwareAction implements GridAction {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(isEnabled(e));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    if (!isEnabled(e)) return;
    DataGrid grid = Objects.requireNonNull(GridUtil.getDataGrid(e.getDataContext()));
    grid.getResultView().growSelection();
  }

  private static boolean isEnabled(AnActionEvent e) {
    DataGrid grid = GridUtil.getDataGrid(e.getDataContext());
    return grid != null && grid.getVisibleRows().size() * grid.getVisibleColumns().size() > 0 &&
           grid.getContextColumn().asInteger() == -1;
  }
}
