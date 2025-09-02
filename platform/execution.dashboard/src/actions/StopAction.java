// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.actions;

import com.intellij.execution.dashboard.RunDashboardRunConfigurationNode;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.ui.RunContentManagerImpl;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.ui.content.Content;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;

import static com.intellij.execution.dashboard.actions.RunDashboardActionUtils.getLeafTargets;

/**
 * @author konstantin.aleev
 */
// fixme again executor action - ask Lera?
final class StopAction extends DumbAwareAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    Presentation presentation = e.getPresentation();
    if (project == null) {
      presentation.setEnabledAndVisible(false);
      return;
    }

    JBIterable<RunDashboardRunConfigurationNode> targetNodes = getLeafTargets(e);
    boolean enabled = targetNodes.filter(node -> {
      Content content = node.getContent();
      return content != null && !RunContentManagerImpl.isTerminated(content);
    }).isNotEmpty();
    presentation.setEnabled(enabled);
    presentation.setVisible(enabled || !e.isFromContextMenu());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    for (RunDashboardRunConfigurationNode node : getLeafTargets(e)) {
      ExecutionManagerImpl.stopProcess(node.getDescriptor());
    }
  }
}
