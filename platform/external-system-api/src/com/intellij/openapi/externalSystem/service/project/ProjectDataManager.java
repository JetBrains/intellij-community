// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataService;
import com.intellij.openapi.externalSystem.service.project.manage.WorkspaceDataService;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.List;

/**
 * Aggregates all {@link ProjectDataService#EP_NAME registered data services}
 * and provides entry points for project data management.
 *
 * @author Vladislav Soroka
 */
public interface ProjectDataManager {
  static ProjectDataManager getInstance() {
    return ApplicationManager.getApplication().getService(ProjectDataManager.class);
  }

  /**
   * @deprecated
   * Use {@link #importData(DataNode, Project)} instead.
   * Service implementation always performs operations synchronously.
   */
  @Deprecated
  default <T> void importData(@NotNull DataNode<T> node,
                              @NotNull Project project,
                              boolean synchronous) {
    importData(node, project);
  }

  <T> void importData(@NotNull DataNode<T> node,
                              @NotNull Project project);

  <T> void importData(@NotNull DataNode<T> node,
                      @NotNull Project project,
                      @NotNull IdeModifiableModelsProvider modelsProvider);


  @NotNull
  List<ProjectDataService<?, ?>> findService(@NotNull Key<?> key);

  @NotNull
  List<WorkspaceDataService<?>> findWorkspaceService(@NotNull Key<?> key);

  void ensureTheDataIsReadyToUse(@Nullable DataNode dataNode);

  @Nullable
  ExternalProjectInfo getExternalProjectData(@NotNull Project project,
                                             @NotNull ProjectSystemId projectSystemId,
                                             @NotNull String externalProjectPath);

  @NotNull @Unmodifiable
  Collection<ExternalProjectInfo> getExternalProjectsData(@NotNull Project project, @NotNull ProjectSystemId projectSystemId);

  /**
   * Returns an instance which can be used to perform massive modifications of the project configurations. {@link IdeModifiableModelsProvider#commit()}
   * must be manually called on the returned instance for these modifications to take effect.
   */
  @NotNull IdeModifiableModelsProvider createModifiableModelsProvider(@NotNull Project project);
}
