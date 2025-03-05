// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model.task.event;

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import org.jetbrains.annotations.NotNull;

public class ExternalSystemTaskExecutionEvent extends ExternalSystemTaskNotificationEvent {

  private final @NotNull ExternalSystemProgressEvent<?> myProgressEvent;

  public ExternalSystemTaskExecutionEvent(@NotNull ExternalSystemTaskId id, @NotNull ExternalSystemProgressEvent<?> progressEvent) {
    super(id, progressEvent.getDisplayName());
    myProgressEvent = progressEvent;
  }

  public @NotNull ExternalSystemProgressEvent<?> getProgressEvent() {
    return myProgressEvent;
  }
}
