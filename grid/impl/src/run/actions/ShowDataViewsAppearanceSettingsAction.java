package com.intellij.database.run.actions;

import com.intellij.database.datagrid.DataGrid;
import com.intellij.database.datagrid.GridHelper;
import com.intellij.database.settings.DataGridAppearanceConfigurable;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.util.PlatformUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.database.DatabaseDataKeys.DATA_GRID_KEY;

public class ShowDataViewsAppearanceSettingsAction extends DumbAwareAction {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }
    DataGrid grid = e.getData(DATA_GRID_KEY);
    boolean isDatabaseHookUp = grid != null && GridHelper.get(grid).isDatabaseHookUp(grid);
    e.getPresentation().setEnabledAndVisible(!PlatformUtils.isDataGrip() || !isDatabaseHookUp);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    ShowSettingsUtilImpl.showSettingsDialog(e.getData(CommonDataKeys.PROJECT), DataGridAppearanceConfigurable.ID, "");
  }
}
