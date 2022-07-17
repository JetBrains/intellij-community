// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.dashboard.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.dashboard.RunDashboardRunConfigurationNode;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;

import static com.intellij.execution.dashboard.actions.RunDashboardActionUtils.getTargets;

/**
 * @author Konstantin Aleev
 */
final class GroupConfigurationsAction extends AnAction {

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Presentation presentation = e.getPresentation();
    presentation.setEnabledAndVisible(e.getProject() != null &&
                                      getTargets(e).isNotEmpty());
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      presentation.setText(getTemplatePresentation().getText() + "...");
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;

    JBIterable<RunDashboardRunConfigurationNode> nodes = getTargets(e);
    RunDashboardRunConfigurationNode firstNode = nodes.first();
    String initialValue = firstNode != null ? firstNode.getConfigurationSettings().getFolderName() : null;
    String value = Messages.showInputDialog(project, ExecutionBundle.message("run.dashboard.group.configurations.label"),
                                            ExecutionBundle.message("run.dashboard.group.configurations.title"), null, initialValue, null);
    if (value == null) return;

    String groupName = value.isEmpty() ? null : value; // If input value is empty then ungroup nodes.

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
