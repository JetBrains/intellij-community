// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.dashboard.RunDashboardRunConfigurationNode;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.execution.dashboard.actions.RunDashboardActionUtils.getTargets;

/**
 * @author Konstantin Aleev
 */
final class GroupConfigurationsAction
  extends DumbAwareAction
  implements ActionRemoteBehaviorSpecification.Frontend {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(e.getProject() != null && !getTargets(e).isEmpty());
    if (e.isFromContextMenu()) {
      presentation.setText(getTemplatePresentation().getText() + "...");
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    List<RunDashboardRunConfigurationNode> nodes = getTargets(e);
    RunDashboardRunConfigurationNode firstNode = ContainerUtil.getFirstItem(nodes);
    String initialValue = firstNode != null ? firstNode.getConfigurationSettings().getFolderName() : null;
    String value = Messages.showInputDialog(project, ExecutionBundle.message("run.dashboard.group.configurations.label"),
                                            ExecutionBundle.message("run.dashboard.group.configurations.title"), null, initialValue, null);
    if (value == null) return;

    String groupName = value.isEmpty() ? null : value; // If input value is empty then ungroup nodes.

    // fixme - delegate to backend following part, ask Lera?
    final RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(project);
    runManager.fireBeginUpdate();
    try {
      nodes.forEach(node -> node.getConfigurationSettings().setFolderName(groupName));
    }
    finally {
      runManager.fireEndUpdate();
    }
  }
}
