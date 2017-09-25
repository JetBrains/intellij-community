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
import com.intellij.execution.dashboard.RunDashboardRunConfigurationNode;
import com.intellij.execution.dashboard.RunDashboardManager;
import com.intellij.execution.impl.ExecutionManagerImpl;
import com.intellij.execution.ui.RunContentManagerImpl;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author konstantin.aleev
 */
public class StopAction extends RunDashboardTreeLeafAction<RunDashboardRunConfigurationNode> {
  public StopAction() {
    super(ExecutionBundle.message("run.dashboard.stop.action.name"),
          ExecutionBundle.message("run.dashboard.stop.action.description"),
          AllIcons.Actions.Suspend);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null || RunDashboardManager.getInstance(project).isShowConfigurations()) {
      List<RunDashboardRunConfigurationNode> targetNodes = getTargetNodes(e);
      e.getPresentation().setEnabled(targetNodes.stream().anyMatch(node -> {
        Content content = node.getContent();
        return content != null && !RunContentManagerImpl.isTerminated(content);
      }));
    }
    else {
      Content content = RunDashboardManager.getInstance(project).getDashboardContentManager().getSelectedContent();
      e.getPresentation().setEnabled(content != null && !RunContentManagerImpl.isTerminated(content));
    }
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null || RunDashboardManager.getInstance(project).isShowConfigurations()) {
      super.actionPerformed(e);
    }
    else {
      Content content = RunDashboardManager.getInstance(project).getDashboardContentManager().getSelectedContent();
      if (content != null) {
        ExecutionManagerImpl.stopProcess(RunContentManagerImpl.getRunContentDescriptorByContent(content));
      }
    }
  }

  @Override
  protected void doActionPerformed(RunDashboardRunConfigurationNode node) {
    ExecutionManagerImpl.stopProcess(node.getDescriptor());
  }

  @Override
  protected Class<RunDashboardRunConfigurationNode> getTargetNodeClass() {
    return RunDashboardRunConfigurationNode.class;
  }
}
