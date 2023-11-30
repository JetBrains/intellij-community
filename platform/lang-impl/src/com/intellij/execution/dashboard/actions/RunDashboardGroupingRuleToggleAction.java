// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.dashboard.actions;

import com.intellij.execution.dashboard.RunDashboardServiceViewContributor;
import com.intellij.execution.services.ServiceEventListener;
import com.intellij.execution.services.ServiceViewActionUtils;
import com.intellij.execution.services.ServiceViewContributor;
import com.intellij.execution.services.ServiceViewOptions;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

abstract class RunDashboardGroupingRuleToggleAction extends ToggleAction implements DumbAware {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    ServiceViewOptions viewOptions = e.getData(ServiceViewActionUtils.OPTIONS_KEY);
    Presentation presentation = e.getPresentation();
    if (viewOptions != null && !viewOptions.isGroupByServiceGroups()) {
      presentation.setEnabledAndVisible(false);
      return;
    }
    Set<ServiceViewContributor> contributors = e.getData(ServiceViewActionUtils.CONTRIBUTORS_KEY);
    if (contributors != null) {
      for (ServiceViewContributor contributor : contributors) {
        if (contributor instanceof RunDashboardServiceViewContributor) {
          presentation.setEnabledAndVisible(true);
          return;
        }
      }
    }
    presentation.setEnabledAndVisible(false);
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return false;

    return PropertiesComponent.getInstance(project).getBoolean(getRuleName(), isEnabledByDefault());
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    Project project = e.getProject();
    if (project == null) return;

    PropertiesComponent.getInstance(project).setValue(getRuleName(), state, isEnabledByDefault());
    project.getMessageBus().syncPublisher(ServiceEventListener.TOPIC).handle(
      ServiceEventListener.ServiceEvent.createResetEvent(RunDashboardServiceViewContributor.class));
  }

  protected abstract @NotNull String getRuleName();

  protected boolean isEnabledByDefault() {
    return true;
  }
}
