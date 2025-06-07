// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.project;

import com.intellij.openapi.externalSystem.importing.ProjectResolverPolicy;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Defines common interface for resolving external system project..
 */
public interface ExternalSystemProjectResolver<S extends ExternalSystemExecutionSettings> {

  /**
   * Builds object-level representation of the external system config file contained at the given path.
   *
   * @param id            id of the current 'resolve project info' task
   * @param projectPath   absolute path to the target external system config file
   * @param isPreviewMode Indicates, that an implementation can not provide/resolve any external dependencies.
   *                      Only project dependencies and local file dependencies may included on the modules' classpath.
   *                      And should not include any 'heavy' tasks like not trivial code generations.
   *                      It is supposed to be fast.
   * @param settings      settings to use for the project resolving;
   *                      {@code null} as indication that no specific settings are required
   * @param listener      callback to be notified about the execution
   * @return object-level representation of the target external system project;
   * {@code null} if it's not possible to resolve the project due to the objective reasons
   * @throws ExternalSystemException  in case when unexpected exception occurs during project info construction
   * @throws IllegalArgumentException if given path is invalid
   * @throws IllegalStateException    if it's not possible to resolve target project info
   */
  default @Nullable DataNode<ProjectData> resolveProjectInfo(@NotNull ExternalSystemTaskId id,
                                           @NotNull String projectPath,
                                           boolean isPreviewMode,
                                           @Nullable S settings,
                                           @NotNull ExternalSystemTaskNotificationListener listener)
    throws ExternalSystemException, IllegalArgumentException, IllegalStateException {
    return resolveProjectInfo(id, projectPath, isPreviewMode, settings, null, listener);
  }

  @ApiStatus.Experimental
  default @Nullable DataNode<ProjectData> resolveProjectInfo(@NotNull ExternalSystemTaskId id,
                                                             @NotNull String projectPath,
                                                             boolean isPreviewMode,
                                                             @Nullable S settings,
                                                             @Nullable ProjectResolverPolicy resolverPolicy,
                                                             @NotNull ExternalSystemTaskNotificationListener listener)
    throws ExternalSystemException, IllegalArgumentException, IllegalStateException {
    return resolveProjectInfo(id, projectPath, isPreviewMode, settings, listener);
  }

  /**
   * @param taskId   id of the 'resolve project info' task
   * @param listener callback to be notified about the cancellation
   * @return true if the task execution was successfully stopped, false otherwise or if target external system does not support the task cancellation
   */
  boolean cancelTask(@NotNull ExternalSystemTaskId taskId, @NotNull ExternalSystemTaskNotificationListener listener);
}

