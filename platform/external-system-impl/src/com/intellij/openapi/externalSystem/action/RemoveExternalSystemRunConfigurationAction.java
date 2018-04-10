/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.action;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.view.ExternalSystemNode;
import com.intellij.openapi.externalSystem.view.RunConfigurationNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 11/13/2014
 */
public class RemoveExternalSystemRunConfigurationAction extends ExternalSystemAction {

  protected boolean isEnabled(AnActionEvent e) {
    if (!super.isEnabled(e)) return false;
    final List<ExternalSystemNode> selectedNodes = ExternalSystemDataKeys.SELECTED_NODES.getData(e.getDataContext());
    if (selectedNodes == null || selectedNodes.size() != 1) return false;
    return selectedNodes.get(0) instanceof RunConfigurationNode;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = getProject(e);
    assert project != null;
    final List<ExternalSystemNode> selectedNodes = ExternalSystemDataKeys.SELECTED_NODES.getData(e.getDataContext());
    if (selectedNodes == null || selectedNodes.size() != 1 || !(selectedNodes.get(0) instanceof RunConfigurationNode)) return;

    RunnerAndConfigurationSettings settings = ((RunConfigurationNode)selectedNodes.get(0)).getSettings();
    assert settings != null;

    int res = Messages.showYesNoDialog(project, "Delete \"" + settings.getName() + "\"?", "Confirmation", Messages.getQuestionIcon());
    if (res == Messages.YES) {
      RunManager.getInstance(project).removeConfiguration(settings);
    }
  }
}
