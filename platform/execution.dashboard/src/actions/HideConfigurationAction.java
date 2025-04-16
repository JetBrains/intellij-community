// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.dashboard.RunDashboardManager;
import com.intellij.execution.dashboard.RunDashboardRunConfigurationNode;
import com.intellij.execution.dashboard.actions.RunDashboardActionUtils;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.platform.execution.dashboard.RunDashboardManagerImpl;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;

final class HideConfigurationAction
  extends DumbAwareAction
  implements ActionRemoteBehaviorSpecification.Frontend{

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    List<RunDashboardRunConfigurationNode> nodes = RunDashboardActionUtils.getTargets(e);
    boolean enabled = e.getProject() != null && !nodes.isEmpty();
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

    List<RunDashboardRunConfigurationNode> nodes = RunDashboardActionUtils.getTargets(e);
    Set<RunConfiguration> configurations = ContainerUtil.map2Set(nodes, node -> node.getConfigurationSettings().getConfiguration());
    ((RunDashboardManagerImpl)RunDashboardManager.getInstance(project)).hideConfigurations(configurations);
  }
}
