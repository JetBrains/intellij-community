package com.intellij.database.run.actions;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.Toggleable;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

/**
* @author Gregory.Shrago
*/
public class ToggleFilterComponentAction extends ToggleAction implements DumbAware, GridAction {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DataGrid dataGrid = GridUtil.getDataGrid(e.getDataContext());
    boolean enabled = dataGrid != null && dataGrid.isFilteringSupported();
    e.getPresentation().setEnabledAndVisible(enabled);
    Toggleable.setSelected(e.getPresentation(), enabled && dataGrid.isFilteringComponentShown());
    super.update(e);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    DataGrid dataGrid = GridUtil.getDataGrid(e.getDataContext());
    return dataGrid != null && dataGrid.isFilteringComponentShown();
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    DataGrid dataGrid = GridUtil.getDataGrid(e.getDataContext());
    if (dataGrid == null) return;
    dataGrid.toggleFilteringComponent();
    dataGrid.getFilterComponent().getFilterPanel().getComponent().requestFocusInWindow();
  }
}
