package com.intellij.database.run.actions;

import com.intellij.database.DataGridBundle;
import com.intellij.database.DatabaseDataKeys;
import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridHelper;
import com.intellij.database.datagrid.GridUtilCore;
import com.intellij.database.run.actions.SetCustomPageSizeAction.SetPageSizeDialogWrapper;
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
    GridHelper helper = GridHelper.get(grid);
    String pageSize = helper.isLimitDefaultPageSize() ? String.valueOf(helper.getDefaultPageSize()) : DataGridBundle.message("action.ChangePageSize.text.all");
    e.getPresentation().setText(DataGridBundle.message("action.SetDefaultPageSize.text.2", pageSize));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    DataGrid grid = e.getData(DatabaseDataKeys.DATA_GRID_KEY);
    if (grid == null) return;
    new SetPageSizeDialogWrapper(getEventProject(e)) {

      @Override
      protected int getPageSize() {
        return GridHelper.get(grid).getDefaultPageSize();
      }

      @Override
      protected boolean isLimitPageSize() {
        return GridHelper.get(grid).isLimitDefaultPageSize();
      }

      @Override
      protected void doOKAction() {
        super.doOKAction();
        GridHelper helper = GridHelper.get(grid);
        int pageSize = myForm.getPageSize();
        if (GridUtilCore.isPageSizeUnlimited(pageSize)) {
          helper.setLimitDefaultPageSize(false);
        }
        else {
          helper.setLimitDefaultPageSize(true);
          helper.setDefaultPageSize(pageSize);
        }
        ChangePageSizeAction.setPageSizeAndReload(pageSize, grid);
      }
    }.show();
  }
}
