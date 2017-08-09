/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.dashboard.actions;

import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.dashboard.RunDashboardContent;
import com.intellij.execution.dashboard.RunDashboardManager;
import com.intellij.execution.dashboard.tree.FolderDashboardGroupingRule;
import com.intellij.execution.dashboard.tree.GroupingNode;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Aleev
 */
public class UngroupConfigurationsActions extends RunDashboardTreeAction<GroupingNode> {
  protected UngroupConfigurationsActions() {
    super(ExecutionBundle.message("run.dashboard.ungroup.configurations.action.name"), null, AllIcons.General.Remove);
  }

  @Override
  protected boolean isVisible4(GroupingNode node) {
    return isEnabled4(node);
  }

  @Override
  protected boolean isEnabled4(GroupingNode node) {
    return node.getGroup() instanceof FolderDashboardGroupingRule.FolderDashboardGroup;
  }

  @Override
  protected boolean isVisibleForAnySelection(@NotNull AnActionEvent e) {
    return false;
  }

  @Override
  protected boolean isMultiSelectionAllowed() {
    return true;
  }

  @Override
  protected Class<GroupingNode> getTargetNodeClass() {
    return GroupingNode.class;
  }

  @Override
  protected void doActionPerformed(@NotNull RunDashboardContent content, AnActionEvent e, GroupingNode node) {
    Project project = e.getProject();
    if (project == null) return;

    final RunManagerImpl runManager = RunManagerImpl.getInstanceImpl(project);
    runManager.fireBeginUpdate();
    try {
      runManager.getAllSettings().forEach(settings -> {
        if (!RunDashboardManager.getInstance(project).isShowInDashboard(settings.getConfiguration())) return;

        if (node.getGroup().getName().equals(settings.getFolderName())) {
          settings.setFolderName(null);
        }
      });
    }
    finally {
      runManager.fireEndUpdate();
    }
  }
}
