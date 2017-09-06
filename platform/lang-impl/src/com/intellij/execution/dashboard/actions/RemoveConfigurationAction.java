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
import com.intellij.execution.RunManager;
import com.intellij.execution.dashboard.RunDashboardRunConfigurationNode;
import com.intellij.execution.dashboard.RunDashboardContent;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author konstantin.aleev
 */
public class RemoveConfigurationAction extends RunConfigurationTreeAction {
  public RemoveConfigurationAction() {
    super(ExecutionBundle.message("run.dashboard.remove.configuration.action.name"),
          ExecutionBundle.message("run.dashboard.remove.configuration.action.description"),
          AllIcons.General.Remove);
  }

  @Override
  protected boolean isEnabled4(RunDashboardRunConfigurationNode node) {
    return RunManager.getInstance(node.getProject()).hasSettings(node.getConfigurationSettings());
  }

  @Override
  protected boolean isMultiSelectionAllowed() {
    return true;
  }

  @Override
  protected void doActionPerformed(@NotNull RunDashboardContent content, AnActionEvent e, List<RunDashboardRunConfigurationNode> nodes) {
    if (Messages.showYesNoDialog((Project)null,
                                 ExecutionBundle.message("run.dashboard.remove.configuration.dialog.message"),
                                 ExecutionBundle.message("run.dashboard.remove.configuration.dialog.title"),
                                 Messages.getWarningIcon())
        != Messages.YES) {
      return;
    }
    super.doActionPerformed(content, e, nodes);
  }

  @Override
  protected void doActionPerformed(RunDashboardRunConfigurationNode node) {
    RunManager.getInstance(node.getProject()).removeConfiguration(node.getConfigurationSettings());
  }
}
