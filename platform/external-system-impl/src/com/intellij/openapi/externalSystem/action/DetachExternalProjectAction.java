/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManager;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.view.ProjectNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 6/13/13 5:42 PM
 */
public class DetachExternalProjectAction extends ExternalSystemNodeAction<ProjectData> {

  public DetachExternalProjectAction() {
    super(ProjectData.class);
    getTemplatePresentation().setText(ExternalSystemBundle.message("action.detach.external.project.text", "external"));
    getTemplatePresentation().setDescription(ExternalSystemBundle.message("action.detach.external.project.description"));
    getTemplatePresentation().setIcon(SystemInfoRt.isMac ? AllIcons.ToolbarDecorator.Mac.Remove : AllIcons.ToolbarDecorator.Remove);
  }

  @Override
  protected boolean isEnabled(AnActionEvent e) {
    if (!super.isEnabled(e)) return false;
    return ExternalSystemDataKeys.SELECTED_PROJECT_NODE.getData(e.getDataContext()) != null;
  }

  @Override
  public void perform(@NotNull final Project project,
                      @NotNull ProjectSystemId projectSystemId,
                      @NotNull ProjectData projectData,
                      @NotNull AnActionEvent e) {

    e.getPresentation().setText(
      ExternalSystemBundle.message("action.detach.external.project.text", projectSystemId.getReadableName())
    );

    final ProjectNode projectNode = ExternalSystemDataKeys.SELECTED_PROJECT_NODE.getData(e.getDataContext());
    assert projectNode != null;

    ExternalSystemApiUtil.getLocalSettings(project, projectSystemId).
      forgetExternalProjects(Collections.singleton(projectData.getLinkedExternalProjectPath()));
    ExternalSystemApiUtil.getSettings(project, projectSystemId).unlinkExternalProject(projectData.getLinkedExternalProjectPath());

    ExternalProjectsManager.getInstance(project).forgetExternalProjectData(projectSystemId, projectData.getLinkedExternalProjectPath());

    // Process orphan modules.
    List<Module> orphanModules = ContainerUtilRt.newArrayList();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (!ExternalSystemApiUtil.isExternalSystemAwareModule(projectSystemId, module)) continue;

      String path = ExternalSystemApiUtil.getExternalRootProjectPath(module);
      if (projectData.getLinkedExternalProjectPath().equals(path)) {
        orphanModules.add(module);
      }
    }

    if (!orphanModules.isEmpty()) {
      projectNode.getGroup().remove(projectNode);
      ProjectDataManager.getInstance().removeData(
        ProjectKeys.MODULE, orphanModules, Collections.<DataNode<ModuleData>>emptyList(), projectData, project, false);
    }
  }
}
