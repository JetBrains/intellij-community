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
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.PlatformFacade;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author Vladislav.Soroka
 * @since 4/13/2015
 */
public interface ProjectDataServiceEx<E, I> extends ProjectDataService<E, I> {

  void importData(@NotNull Collection<DataNode<E>> toImport,
                  @Nullable ProjectData projectData,
                  @NotNull Project project,
                  @NotNull PlatformFacade platformFacade,
                  boolean synchronous);

  @NotNull
  Computable<Collection<I>> computeOrphanData(@NotNull Collection<DataNode<E>> toImport,
                                              @NotNull ProjectData projectData,
                                              @NotNull Project project,
                                              @NotNull PlatformFacade platformFacade);

  void removeData(@NotNull Computable<Collection<I>> toRemove,
                  @NotNull Collection<DataNode<E>> toIgnore,
                  @NotNull ProjectData projectData,
                  @NotNull Project project,
                  @NotNull PlatformFacade platformFacade,
                  boolean synchronous);
}
