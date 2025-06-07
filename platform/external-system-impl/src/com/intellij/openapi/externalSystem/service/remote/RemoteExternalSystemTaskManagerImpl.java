// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.service.remote;

import com.intellij.openapi.externalSystem.model.ExternalSystemException;
import com.intellij.openapi.externalSystem.model.settings.ExternalSystemExecutionSettings;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.task.ExternalSystemTaskManager;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class RemoteExternalSystemTaskManagerImpl<S extends ExternalSystemExecutionSettings>
  extends AbstractRemoteExternalSystemService<S> implements RemoteExternalSystemTaskManager<S> {

  private final @NotNull ExternalSystemTaskManager<S> myDelegate;

  public RemoteExternalSystemTaskManagerImpl(@NotNull ExternalSystemTaskManager<S> delegate) {
    myDelegate = delegate;
  }

  @Override
  public void executeTasks(
    @NotNull String projectPath,
    @NotNull ExternalSystemTaskId id,
    @NotNull S settings
  ) throws ExternalSystemException {
    execute(id, () -> {
      myDelegate.executeTasks(projectPath, id, settings, getNotificationListener());
      return null;
    });
  }

  @Override
  public boolean cancelTask(
    @NotNull ExternalSystemTaskId id
  ) throws ExternalSystemException {
    return myDelegate.cancelTask(id, getNotificationListener());
  }
}
