// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.notification;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import org.jetbrains.annotations.NotNull;

public interface ExternalSystemProgressNotificationManager {

  /**
   * Allows to register given listener to listen events from all tasks.
   *
   * @param listener listener to register
   * @return {@code true} if given listener was not registered before; {@code false} otherwise
   */
  boolean addNotificationListener(@NotNull ExternalSystemTaskNotificationListener listener);

  /**
   * Allows to register given listener to listen events from all tasks.
   *
   * @param listener         listener to register
   * @param parentDisposable controls the lifetime of the listener
   * @return {@code true} if given listener was not registered before; {@code false} otherwise
   */
  boolean addNotificationListener(@NotNull ExternalSystemTaskNotificationListener listener, @NotNull Disposable parentDisposable);

  /**
   * Allows to register given listener within the current manager for listening events from the task with the target id.
   *
   * @param taskId   target task's id
   * @param listener listener to register
   * @return {@code true} if given listener was not registered before for the given key; {@code false} otherwise
   */
  boolean addNotificationListener(@NotNull ExternalSystemTaskId taskId, @NotNull ExternalSystemTaskNotificationListener listener);

  /**
   * Allows to de-register given listener from the current manager
   *
   * @param listener listener to de-register
   * @return {@code true} if given listener was successfully de-registered; {@code false} if given listener was not registered before
   */
  boolean removeNotificationListener(@NotNull ExternalSystemTaskNotificationListener listener);

  static ExternalSystemProgressNotificationManager getInstance() {
    return ApplicationManager.getApplication().getService(ExternalSystemProgressNotificationManager.class);
  }
}
