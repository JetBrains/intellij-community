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
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ExternalConfigPathAware;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.ProjectDataManager;
import com.intellij.openapi.externalSystem.statistics.ExternalSystemActionsCollector;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.externalSystem.view.ExternalSystemNode;
import com.intellij.openapi.externalSystem.view.ModuleNode;
import com.intellij.openapi.externalSystem.view.ProjectNode;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Vladislav.Soroka
 */
public class IgnoreExternalProjectAction extends ExternalSystemToggleAction {

  private static final Logger LOG = Logger.getInstance(IgnoreExternalProjectAction.class);

  public IgnoreExternalProjectAction() {
    getTemplatePresentation().setText(ExternalSystemBundle.message("action.ignore.external.projects.text", "external", "project"));
    getTemplatePresentation()
      .setDescription(ExternalSystemBundle.message("action.ignore.external.projects.description", "external", "project"));
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    final ProjectSystemId projectSystemId = getSystemId(e);
    final List<ExternalSystemNode<ExternalConfigPathAware>> projectNodes = getProjectNodes(e);
    if (projectNodes.isEmpty()) return;

    final Project project = getProject(e);
    ExternalSystemActionsCollector.trigger(project, projectSystemId, this, e);

    projectNodes.forEach(projectNode -> projectNode.setIgnored(state));

    Set<DataNode<ProjectData>> uniqueExternalProjects = projectNodes.stream()
      .map(
        projectNode -> {
          final String externalProjectPath = projectNode.getData().getLinkedExternalProjectPath();
          final ExternalProjectInfo externalProjectInfo =
            ExternalSystemUtil.getExternalProjectInfo(project, projectSystemId, externalProjectPath);
          final DataNode<ProjectData> projectDataNode =
            externalProjectInfo == null ? null : externalProjectInfo.getExternalProjectStructure();

          if (projectDataNode == null && LOG.isDebugEnabled()) {
            LOG.debug(String.format("external project data not found, path: %s, data: %s", externalProjectPath, externalProjectInfo));
          }
          return projectDataNode;
        }
      )
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());

    // async import to not block UI on big projects
    ProgressManager.getInstance().run(new Task.Backgroundable(project, e.getPresentation().getText(), false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        uniqueExternalProjects.forEach(
          externalProjectInfo -> ServiceManager.getService(ProjectDataManager.class).importData(externalProjectInfo, project, true)
        );
      }
    });
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    boolean selected = super.isSelected(e);
    ProjectSystemId systemId = getSystemId(e);
    final String systemIdName = systemId != null ? systemId.getReadableName() : "external";
    final String pluralizedProjects = StringUtil.pluralize("project", getProjectNodes(e).size());
    if (selected) {
      setText(e, ExternalSystemBundle.message("action.unignore.external.projects.text", systemIdName, pluralizedProjects));
      setDescription(e, ExternalSystemBundle.message("action.unignore.external.projects.description", systemIdName, pluralizedProjects));
    }
    else {
      setText(e, ExternalSystemBundle.message("action.ignore.external.projects.text", systemIdName, pluralizedProjects));
      setDescription(e, ExternalSystemBundle.message("action.ignore.external.projects.description", systemIdName, pluralizedProjects));
    }
    return selected;
  }

  @Override
  protected boolean isEnabled(@NotNull AnActionEvent e) {
    if (!super.isEnabled(e)) return false;
    return !getProjectNodes(e).isEmpty();
  }

  @Override
  protected boolean doIsSelected(@NotNull AnActionEvent e) {
    return ContainerUtil.exists(getProjectNodes(e), projectNode -> projectNode.isIgnored());
  }

  @NotNull
  private static List<ExternalSystemNode<ExternalConfigPathAware>> getProjectNodes(@NotNull AnActionEvent e) {
    final List<ExternalSystemNode> selectedNodes = ExternalSystemDataKeys.SELECTED_NODES.getData(e.getDataContext());
    if (selectedNodes == null || selectedNodes.isEmpty()) return Collections.emptyList();

    return selectedNodes.stream()
      .map(node -> (node instanceof ModuleNode || node instanceof ProjectNode) ? (ExternalSystemNode<ExternalConfigPathAware>)node : null)
      .filter(Objects::nonNull)
      .collect(Collectors.toList());
  }
}
