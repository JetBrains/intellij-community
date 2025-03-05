// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model.task.event;

import com.intellij.build.events.BuildEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Experimental
public class ExternalSystemBuildEvent extends ExternalSystemTaskNotificationEvent {

  private final @NotNull BuildEvent myBuildEvent;

  public ExternalSystemBuildEvent(@NotNull ExternalSystemTaskId id, @NotNull BuildEvent buildEvent) {
    super(id, buildEvent.getMessage());
    myBuildEvent = buildEvent;
  }

  public @NotNull BuildEvent getBuildEvent() {
    return myBuildEvent;
  }
}
