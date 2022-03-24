// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.util.DisposeAwareProjectChange;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

@Order(ExternalSystemConstants.BUILTIN_PROJECT_DATA_SERVICE_ORDER)
public final class ProjectDataServiceImpl extends AbstractProjectDataService<ProjectData, Project> {
  @Override
  public @NotNull Key<ProjectData> getTargetDataKey() {
    return ProjectKeys.PROJECT;
  }

  @Override
  public void importData(@NotNull Collection<? extends DataNode<ProjectData>> toImport,
                         @Nullable ProjectData projectData,
                         final @NotNull Project project,
                         @NotNull IdeModifiableModelsProvider modelsProvider) {
    // root project can be marked as ignored
    if (toImport.isEmpty()) {
      return;
    }

    if (toImport.size() != 1) {
      throw new IllegalArgumentException(String.format("Expected to get a single project but got %d: %s", toImport.size(), toImport));
    }
    DataNode<ProjectData> node = toImport.iterator().next();
    assert projectData == node.getData();

    if (!ExternalSystemApiUtil.isOneToOneMapping(project, node.getData(), modelsProvider.getModules())) {
      return;
    }
    
    if (!project.getName().equals(projectData.getInternalName())) {
      renameProject(projectData.getInternalName(), projectData.getOwner(), project);
    }
  }

  private static void renameProject(final @NotNull String newName,
                                    final @NotNull ProjectSystemId externalSystemId,
                                    final @NotNull Project project)
  {
    if (!(project instanceof ProjectEx) || newName.equals(project.getName())) {
      return;
    }
    ExternalSystemApiUtil.executeProjectChangeAction(true, new DisposeAwareProjectChange(project) {
      @Override
      public void execute() {
        String oldName = project.getName();
        ((ProjectEx)project).setProjectName(newName);
        ExternalSystemApiUtil.getSettings(project, externalSystemId).getPublisher().onProjectRenamed(oldName, newName);
      }
    });
  }
}
