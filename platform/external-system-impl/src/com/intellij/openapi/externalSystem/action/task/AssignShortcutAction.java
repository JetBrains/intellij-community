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

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.externalSystem.action.ExternalSystemNodeAction;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.task.TaskData;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManager;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalSystemKeymapExtension;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalSystemShortcutsManager;
import com.intellij.openapi.externalSystem.view.ExternalSystemNode;
import com.intellij.openapi.externalSystem.view.ModuleNode;
import com.intellij.openapi.externalSystem.view.ProjectNode;
import com.intellij.openapi.keymap.impl.ui.EditKeymapsDialog;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
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
  protected boolean isEnabled(AnActionEvent e) {
    return super.isEnabled(e) && !isIgnoredNode(e);
  }

  @Override
  protected void perform(@NotNull Project project,
                         @NotNull ProjectSystemId projectSystemId,
                         @NotNull TaskData taskData,
                         @NotNull AnActionEvent e) {
    final ExternalSystemShortcutsManager shortcutsManager = ExternalProjectsManager.getInstance(project).getShortcutsManager();
    final String actionId = shortcutsManager.getActionId(taskData.getLinkedExternalProjectPath(), taskData.getName());
    if (actionId != null) {
      AnAction action = ActionManager.getInstance().getAction(actionId);
      if (action == null) {
        ExternalSystemNode<?> taskNode = ContainerUtil.getFirstItem(ExternalSystemDataKeys.SELECTED_NODES.getData(e.getDataContext()));
        assert taskNode != null;
        final String group;
        final ModuleNode moduleDataNode = taskNode.findParent(ModuleNode.class);
        if (moduleDataNode != null) {
          ModuleData moduleData = moduleDataNode.getData();
          group = moduleData != null ? moduleData.getInternalName() : null;
        }
        else {
          ProjectNode projectNode = taskNode.findParent(ProjectNode.class);
          ProjectData projectData = projectNode != null ? projectNode.getData() : null;
          group = projectData != null ? projectData.getInternalName() : null;
        }
        if (group != null) {
          ExternalSystemKeymapExtension.getOrRegisterAction(project, group, taskData);
        }
      }
      new EditKeymapsDialog(project, actionId).show();
    }
  }
}
