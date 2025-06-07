// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model.task;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * Encapsulates information about processing state change of the {@link #getId() target task}.
 */
public class ExternalSystemTaskNotificationEvent implements Serializable {

  private static final long serialVersionUID = 1L;

  private final ExternalSystemTaskId myId;
  private final String               myDescription;

  public ExternalSystemTaskNotificationEvent(@NotNull ExternalSystemTaskId id, @NotNull String description) {
    myId = id;
    myDescription = description;
  }

  public @NotNull ExternalSystemTaskId getId() {
    return myId;
  }

  public @NotNull String getDescription() {
    return myDescription;
  }

  @Override
  public int hashCode() {
    return 31 * myDescription.hashCode() + myId.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ExternalSystemTaskNotificationEvent that = (ExternalSystemTaskNotificationEvent)o;
    return myId.equals(that.myId) && myDescription.equals(that.myDescription);
  }

  @Override
  public String toString() {
    return myId + "-" + myDescription;
  }
}
