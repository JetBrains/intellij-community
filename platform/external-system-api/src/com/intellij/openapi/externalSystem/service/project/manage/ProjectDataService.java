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
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Defines common contract for a strategy which is able to manage project data defines in terms of 'external systems' sub-system.
 * <p/>
 * Implementations of this interface are expected to be thread-safe.
 * <p/>
 * <b>Note:</b> there is a possible case that more than one service is registered for the same key, e.g. there is a built-in
 * generic service and a plugin provides custom extension to it. Platform calls all of them then. That means that project data
 * service implementation must be ready to the following:
 * <pre>
 * <ul>
 *   <li>target data is already imported by another service (just no-op then);</li>
 *   <li>
 *     provide a hint about the order in which the services for the same key should be processed. That is done by marking
 *     it by {@link Order} annotation where the smaller value corresponds to earlier execution;
 *   </li>
 * </ul>
 * </pre>
 * 
 * 
 * @author Denis Zhdanov
 * @since 4/12/13 3:59 PM
 * @param <E>  target external project data type
 * @param <I>  target ide project data type
 */
public interface ProjectDataService<E, I> {

  ExtensionPointName<ProjectDataService<?, ?>> EP_NAME = ExtensionPointName.create("com.intellij.externalProjectDataService");

  /**
   * @return key of project data supported by the current manager
   */
  @NotNull
  Key<E> getTargetDataKey();

  /**
   * It's assumed that given data nodes present at the ide when this method returns. I.e. the method should behave as below for
   * every of the given data nodes:
   * <pre>
   * <ul>
   *   <li>there is an existing project entity for the given data node and it has the same state. Do nothing for it then;</li>
   *   <li>
   *     there is an existing project entity for the given data node but it has different state (e.g. a module dependency
   *     is configured as 'exported' at the ide but not at external system). Reset the state to the external system's one then;
   *   </li>
   *   <li> there is no corresponding project entity at the ide side. Create it then; </li>
   * </ul>
   * </pre>
   * are created, updated or left as-is if they have the
   * 
   * @param toImport
   * @param project
   */
  void importData(@NotNull Collection<DataNode<E>> toImport,
                  @Nullable ProjectData projectData,
                  @NotNull Project project,
                  @NotNull IdeModifiableModelsProvider modelsProvider);

  /**
   * Compute orphan data.
   */
  @NotNull
  Computable<Collection<I>> computeOrphanData(@NotNull Collection<DataNode<E>> toImport,
                                              @NotNull ProjectData projectData,
                                              @NotNull Project project,
                                              @NotNull IdeModifiableModelsProvider modelsProvider);

  /**
   * Asks to remove all given ide project entities.
   * <p/>
   * <b>Note:</b> as more than one {@link ProjectDataService} might be configured for a target entity type, there is a possible case
   * that the entities have already been removed when this method is called. Then it's necessary to cleanup auxiliary data (if any)
   * or just return otherwise.
   * 
   * @param toRemove     project entities to remove
   * @param project      target project
   */
  void removeData(@NotNull Computable<Collection<I>> toRemove,
                  @NotNull Collection<DataNode<E>> toIgnore,
                  @NotNull ProjectData projectData,
                  @NotNull Project project,
                  @NotNull IdeModifiableModelsProvider modelsProvider);
}
