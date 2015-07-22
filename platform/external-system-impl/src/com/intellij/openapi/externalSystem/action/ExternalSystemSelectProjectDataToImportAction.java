/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.externalSystem.service.ui.ExternalProjectDataSelectorDialog;
import com.intellij.openapi.externalSystem.view.ExternalSystemNode;
import com.intellij.openapi.externalSystem.view.ProjectNode;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;

import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 5/12/2015
 */
public class ExternalSystemSelectProjectDataToImportAction extends ExternalSystemAction {

  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = getProject(e);
    final ProjectSystemId projectSystemId = getSystemId(e);

    final List<ExternalSystemNode> selectedNodes = ExternalSystemDataKeys.SELECTED_NODES.getData(e.getDataContext());

    final ExternalProjectInfo projectInfo;
    final ExternalSystemNode<?> externalSystemNode = ContainerUtil.getFirstItem(selectedNodes);
    if (externalSystemNode == null) {
      projectInfo = ContainerUtil.getFirstItem(ProjectDataManager.getInstance().getExternalProjectsData(project, projectSystemId));
    }
    else {
      final ProjectNode projectNode =
        externalSystemNode instanceof ProjectNode ? (ProjectNode)externalSystemNode : externalSystemNode.findParent(ProjectNode.class);
      assert projectNode != null;

      final ProjectData projectData = projectNode.getData();
      assert projectData != null;
      projectInfo =
        ProjectDataManager.getInstance().getExternalProjectData(project, projectSystemId, projectData.getLinkedExternalProjectPath());
    }

    final ExternalProjectDataSelectorDialog dialog;
    if (projectInfo != null) {
      dialog = new ExternalProjectDataSelectorDialog(project, projectInfo, externalSystemNode != null ? externalSystemNode.getData() : null);
      dialog.showAndGet();
    }
  }
}