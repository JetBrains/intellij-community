// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.dashboard.actions;

import com.intellij.execution.dashboard.RunDashboardManager;
import com.intellij.execution.dashboard.RunDashboardManagerImpl;
import com.intellij.execution.dashboard.RunDashboardRunConfigurationStatus;
import com.intellij.execution.dashboard.RunDashboardServiceViewContributor;
import com.intellij.execution.dashboard.tree.RunDashboardStatusFilter;
import com.intellij.execution.services.ServiceViewActionUtils;
import com.intellij.execution.services.ServiceViewContributor;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static com.intellij.execution.dashboard.RunDashboardRunConfigurationStatus.*;

final class RunDashboardFilterActionGroup extends DefaultActionGroup implements CheckedActionGroup, DumbAware {

  @SuppressWarnings("unused")
  RunDashboardFilterActionGroup() {
    this(null, false);
  }

  RunDashboardFilterActionGroup(@Nullable @NlsActions.ActionText String shortName, boolean popup) {
    super(shortName, popup);
    RunDashboardRunConfigurationStatus[] statuses = new RunDashboardRunConfigurationStatus[]{STARTED, FAILED, STOPPED, CONFIGURED};
    for (RunDashboardRunConfigurationStatus status : statuses) {
      add(new RunDashboardStatusFilterToggleAction(status));
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Set<ServiceViewContributor> contributors = e.getData(ServiceViewActionUtils.CONTRIBUTORS_KEY);

    boolean isEnabled = false;
    if (contributors != null) {
      for (ServiceViewContributor contributor : contributors) {
        if (contributor instanceof RunDashboardServiceViewContributor) {
          isEnabled = true;
          break;
        }
      }
    }

    e.getPresentation().setEnabledAndVisible(isEnabled);
  }

  private static class RunDashboardStatusFilterToggleAction extends ToggleAction implements DumbAware {
    private final RunDashboardRunConfigurationStatus myStatus;

    RunDashboardStatusFilterToggleAction(RunDashboardRunConfigurationStatus status) {
      super(status.getName());
      myStatus = status;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
      Project project = e.getProject();
      if (project == null) return false;

      RunDashboardStatusFilter statusFilter = ((RunDashboardManagerImpl)RunDashboardManager.getInstance(project)).getStatusFilter();
      return statusFilter.isVisible(myStatus);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return ActionUpdateThread.EDT;
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
      Project project = e.getProject();
      if (project == null) return;

      RunDashboardManagerImpl manager = (RunDashboardManagerImpl)RunDashboardManager.getInstance(project);
      RunDashboardStatusFilter statusFilter = manager.getStatusFilter();
      if (state) {
        statusFilter.show(myStatus);
      }
      else {
        statusFilter.hide(myStatus);
      }
      manager.updateDashboard(true);
    }
  }
}
