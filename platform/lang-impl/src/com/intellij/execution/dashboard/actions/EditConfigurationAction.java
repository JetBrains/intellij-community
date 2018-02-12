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
import com.intellij.execution.impl.RunDialog;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class EditConfigurationAction extends RunConfigurationTreeAction {
  public EditConfigurationAction() {
    super(ExecutionBundle.message("run.dashboard.edit.configuration.action.name"),
          ExecutionBundle.message("run.dashboard.edit.configuration.action.name"),
          AllIcons.Actions.EditSource);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    if (ActionPlaces.isPopupPlace(e.getPlace())) {
      e.getPresentation().setText(ExecutionBundle.message("run.dashboard.edit.configuration.action.name") + "...");
    }
  }

  @Override
  protected boolean isEnabled4(RunDashboardRunConfigurationNode node) {
    return RunManager.getInstance(node.getProject()).hasSettings(node.getConfigurationSettings());
  }

  @Override
  protected void doActionPerformed(RunDashboardRunConfigurationNode node) {
    RunDialog.editConfiguration(node.getProject(), node.getConfigurationSettings(),
                                ExecutionBundle.message("run.dashboard.edit.configuration.dialog.title"));
  }
}
