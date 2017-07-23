/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.service.project;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataService;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Aggregates all {@link ProjectDataService#EP_NAME registered data services}
 * and provides entry points for project data management.
 *
 * @author Vladislav Soroka
 * @since 4/16/13 11:38 AM
 */
public interface ProjectDataManager {
  static ProjectDataManager getInstance() {
    return ServiceManager.getService(ProjectDataManager.class);
  }

  @SuppressWarnings("unchecked")
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

  void ensureTheDataIsReadyToUse(@Nullable DataNode dataNode);

  @Nullable
  ExternalProjectInfo getExternalProjectData(@NotNull Project project,
                                             @NotNull ProjectSystemId projectSystemId,
                                             @NotNull String externalProjectPath);

  @NotNull
  Collection<ExternalProjectInfo> getExternalProjectsData(@NotNull Project project, @NotNull ProjectSystemId projectSystemId);
}
