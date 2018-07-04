// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.dashboard.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.dashboard.RunDashboardContent;
import com.intellij.execution.dashboard.RunDashboardManager;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Aleev
 */
public class ShowConfigurationsAction extends ToggleAction implements DumbAware {
  public ShowConfigurationsAction() {
    super(ExecutionBundle.message("run.dashboard.show.configurations.action.name"), null, AllIcons.Actions.ShowAsTree);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    RunDashboardContent runDashboardContent = e.getData(RunDashboardContent.KEY);
    if (runDashboardContent == null) {
      e.getPresentation().setEnabledAndVisible(false);
      return;
    }

    e.getPresentation().setEnabledAndVisible(true);
    super.update(e);
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return true;

    return RunDashboardManager.getInstance(project).isShowConfigurations();
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    Project project = e.getProject();
    if (project == null) return;

    RunDashboardManager.getInstance(project).setShowConfigurations(state);
  }
}
