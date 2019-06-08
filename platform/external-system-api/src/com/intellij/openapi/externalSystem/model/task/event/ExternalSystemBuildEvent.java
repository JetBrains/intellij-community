// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  private static final long serialVersionUID = 1L;

  @NotNull private final BuildEvent myBuildEvent;

  public ExternalSystemBuildEvent(@NotNull ExternalSystemTaskId id, @NotNull BuildEvent buildEvent) {
    super(id, buildEvent.getMessage());
    myBuildEvent = buildEvent;
  }

  @NotNull
  public BuildEvent getBuildEvent() {
    return myBuildEvent;
  }
}
