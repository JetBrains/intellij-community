// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataService;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    return ServiceManager.getService(ProjectDataManager.class);
  }

  void importData(@NotNull Collection<DataNode<?>> nodes,
                  @NotNull Project project,
                  @NotNull IdeModifiableModelsProvider modelsProvider,
                  boolean synchronous);

  <T> void importData(@NotNull Collection<DataNode<T>> nodes, @NotNull Project project, boolean synchronous);

  <T> void importData(@NotNull DataNode<T> node,
                      @NotNull Project project,
                      @NotNull IdeModifiableModelsProvider modelsProvider,
                      boolean synchronous);

  <T> void importData(@NotNull DataNode<T> node,
                      @NotNull Project project,
                      boolean synchronous);

  List<ProjectDataService<?, ?>> findService(@NotNull Key<?> key);

  void ensureTheDataIsReadyToUse(@Nullable DataNode dataNode);

  @Nullable
  ExternalProjectInfo getExternalProjectData(@NotNull Project project,
                                             @NotNull ProjectSystemId projectSystemId,
                                             @NotNull String externalProjectPath);

  @NotNull
  Collection<ExternalProjectInfo> getExternalProjectsData(@NotNull Project project, @NotNull ProjectSystemId projectSystemId);
}
