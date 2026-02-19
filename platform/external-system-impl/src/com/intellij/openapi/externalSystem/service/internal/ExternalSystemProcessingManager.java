// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.internal;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTask;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskState;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Provides external system tasks monitoring and management facilities.
 * <p/>
 * Thread-safe.
 */
@ApiStatus.NonExtendable
public interface ExternalSystemProcessingManager {

  /**
   * Allows checking if any task of the given type is being executed at the moment.
   *
   * @param type  target task type
   * @return      {@code true} if any task of the given type is being executed at the moment;
   *              {@code false} otherwise
   */
  boolean hasTaskOfTypeInProgress(
    @NotNull ExternalSystemTaskType type,
    @NotNull Project project
  );

  @Nullable ExternalSystemTask findTask(
    @NotNull ExternalSystemTaskId id
  );

  @Nullable ExternalSystemTask findTask(
    @NotNull ExternalSystemTaskType type,
    @NotNull ProjectSystemId projectSystemId,
    @NotNull String externalProjectPath
  );

  @NotNull List<ExternalSystemTask> findTasksOfState(
    @NotNull ProjectSystemId projectSystemId,
    ExternalSystemTaskState @NotNull ... taskStates
  );

  void add(@NotNull ExternalSystemTask task);

  void release(@NotNull ExternalSystemTaskId id);

  static ExternalSystemProcessingManager getInstance() {
    Application application = ApplicationManager.getApplication();
    return application.getService(ExternalSystemProcessingManager.class);
  }
}
