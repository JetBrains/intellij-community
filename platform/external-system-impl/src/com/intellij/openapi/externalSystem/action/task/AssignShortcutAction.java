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
package com.intellij.openapi.externalSystem.action.task;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.externalSystem.action.ExternalSystemNodeAction;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManager;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalSystemShortcutsManager;
import com.intellij.openapi.keymap.impl.ui.EditKeymapsDialog;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Soroka
 * @since 10/28/2014
 */
public class AssignShortcutAction extends ExternalSystemNodeAction<TaskData> {

  public AssignShortcutAction() {
    super(TaskData.class);
  }

  @Override
  protected void perform(@NotNull Project project,
                         @NotNull ProjectSystemId projectSystemId,
                         @NotNull TaskData taskData,
                         @NotNull AnActionEvent e) {
    final ExternalSystemShortcutsManager shortcutsManager = ExternalProjectsManager.getInstance(project).getShortcutsManager();
    String actionId = shortcutsManager.getActionId(taskData.getLinkedExternalProjectPath(), taskData.getName());
    if (actionId != null) {
      new EditKeymapsDialog(project, actionId).show();
    }
  }
}
