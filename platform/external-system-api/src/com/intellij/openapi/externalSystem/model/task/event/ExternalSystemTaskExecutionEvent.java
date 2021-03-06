// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.task.event;

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import org.jetbrains.annotations.NotNull;

public class ExternalSystemTaskExecutionEvent extends ExternalSystemTaskNotificationEvent {
  private static final long serialVersionUID = 1L;

  @NotNull private final ExternalSystemProgressEvent<?> myProgressEvent;

  public ExternalSystemTaskExecutionEvent(@NotNull ExternalSystemTaskId id, @NotNull ExternalSystemProgressEvent<?> progressEvent) {
    super(id, progressEvent.getDisplayName());
    myProgressEvent = progressEvent;
  }

  @NotNull
  public ExternalSystemProgressEvent<?> getProgressEvent() {
    return myProgressEvent;
  }
}
