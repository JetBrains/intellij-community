// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.platform.execution.dashboard.splitApi.frontend.FrontendRunDashboardService;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.platform.execution.dashboard.actions.RunDashboardActionUtilsKt.getSelectedNodes;
import static com.intellij.platform.execution.dashboard.actions.RunDashboardActionUtilsKt.scheduleHideConfiguration;

final class HideConfigurationAction
  extends DumbAwareAction
  implements ActionRemoteBehaviorSpecification.Frontend {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    List<FrontendRunDashboardService> nodes = project == null ? null : getSelectedNodes(e);
    boolean enabled = project != null && !nodes.isEmpty();
    Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(enabled);
    if (enabled) {
      presentation.setText(ExecutionBundle.message("run.dashboard.hide.configuration.action.name", nodes.size()));
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    List<FrontendRunDashboardService> nodes = getSelectedNodes(e);
    scheduleHideConfiguration(project, ContainerUtil.map(nodes, it -> it.getRunDashboardServiceDto().getUuid()));
  }
}
