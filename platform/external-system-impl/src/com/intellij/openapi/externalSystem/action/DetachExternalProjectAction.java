// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.action;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.manage.ExternalProjectsManagerImpl;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManagerImpl;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemLocalSettings;
import com.intellij.openapi.externalSystem.settings.AbstractExternalSystemSettings;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.externalSystem.util.ExternalSystemTelemetryUtil;
import com.intellij.openapi.externalSystem.view.ExternalSystemNode;
import com.intellij.openapi.externalSystem.view.ProjectNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ApiStatus.Internal
public class DetachExternalProjectAction extends ExternalSystemNodeAction<ProjectData> {

  public DetachExternalProjectAction() {
    super(ProjectData.class);
    getTemplatePresentation().setText(ExternalSystemBundle.messagePointer("action.detach.external.project.text", "External"));
    getTemplatePresentation().setDescription(ExternalSystemBundle.messagePointer("action.detach.external.project.description"));
    getTemplatePresentation().setIcon(AllIcons.General.Remove);
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    if(this.getClass() != DetachExternalProjectAction.class) return;

    ProjectSystemId systemId = getSystemId(e);
    final String systemIdName = systemId != null ? systemId.getReadableName() : "External";
    Presentation presentation = e.getPresentation();
    presentation.setText(ExternalSystemBundle.messagePointer("action.detach.external.project.text", systemIdName));
  }

  @Override
  protected boolean isEnabled(@NotNull AnActionEvent e) {
    if (!super.isEnabled(e)) return false;
    return e.getData(ExternalSystemDataKeys.SELECTED_PROJECT_NODE) != null;
  }

  @Override
  public void perform(final @NotNull Project project,
                      @NotNull ProjectSystemId projectSystemId,
                      @NotNull ProjectData projectData,
                      @NotNull AnActionEvent e) {

    e.getPresentation().setText(
      ExternalSystemBundle.messagePointer("action.detach.external.project.text", projectSystemId.getReadableName())
    );

    final ProjectNode projectNode = e.getData(ExternalSystemDataKeys.SELECTED_PROJECT_NODE);
    assert projectNode != null;
    detachProject(project, projectSystemId, projectData, projectNode);
  }

  public static void detachProject(
    @NotNull Project project,
    @NotNull ProjectSystemId projectSystemId,
    @NotNull ProjectData projectData,
    @Nullable ProjectNode projectNode
  ) {
    String externalProjectPath = projectData.getLinkedExternalProjectPath();

    ExternalSystemTelemetryUtil.runWithSpan(projectSystemId, "Remove project from local settings", __ -> {
      AbstractExternalSystemLocalSettings<?> localSettings = ExternalSystemApiUtil.getLocalSettings(project, projectSystemId);
      localSettings.forgetExternalProjects(Collections.singleton(externalProjectPath));
    });

    ExternalSystemTelemetryUtil.runWithSpan(projectSystemId, "Remove project from system settings", __ -> {
      AbstractExternalSystemSettings<?, ?, ?> settings = ExternalSystemApiUtil.getSettings(project, projectSystemId);
      settings.unlinkExternalProject(externalProjectPath);
    });

    ExternalSystemTelemetryUtil.runWithSpan(projectSystemId, "Remove project from data storage", __ -> {
      ExternalProjectsManagerImpl externalProjectsManager = ExternalProjectsManagerImpl.getInstance(project);
      externalProjectsManager.forgetExternalProjectData(projectSystemId, externalProjectPath);
    });

    ExternalSystemTelemetryUtil.runWithSpan(projectSystemId, "Remove project from tool window", __ -> {
      if (projectNode != null) {
        ExternalSystemNode<?> group = projectNode.getGroup();
        if (group != null) {
          group.remove(projectNode);
        }
      }
    });

    ExternalSystemTelemetryUtil.runWithSpan(projectSystemId, "Remove project from workspace model", __ -> {
      List<Module> orphanModules = collectExternalSystemModules(project, projectSystemId, externalProjectPath);
      if (!orphanModules.isEmpty()) {
        ProjectDataManagerImpl projectDataManager = ProjectDataManagerImpl.getInstance();
        projectDataManager.removeData(ProjectKeys.MODULE, orphanModules, Collections.emptyList(), projectData, project, false);
      }
    });
  }

  private static @NotNull List<Module> collectExternalSystemModules(
    @NotNull Project project,
    @NotNull ProjectSystemId externalSystemId,
    @NotNull String externalProjectPath
  ) {
    List<Module> result = new ArrayList<>();
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (ExternalSystemApiUtil.isExternalSystemAwareModule(externalSystemId, module)) {
        String path = ExternalSystemApiUtil.getExternalRootProjectPath(module);
        if (externalProjectPath.equals(path)) {
          result.add(module);
        }
      }
    }
    return result;
  }
}
