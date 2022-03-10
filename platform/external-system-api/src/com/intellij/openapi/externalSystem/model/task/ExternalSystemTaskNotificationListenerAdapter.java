// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.task;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Denis Zhdanov
 */
public abstract class ExternalSystemTaskNotificationListenerAdapter implements ExternalSystemTaskNotificationListener {

  @NotNull public static final ExternalSystemTaskNotificationListener NULL_OBJECT = new ExternalSystemTaskNotificationListenerAdapter() {
  };
  @Nullable
  private final ExternalSystemTaskNotificationListener myDelegate;

  public ExternalSystemTaskNotificationListenerAdapter() {
    this(null);
  }

  public ExternalSystemTaskNotificationListenerAdapter(@Nullable ExternalSystemTaskNotificationListener delegate) {
    myDelegate = delegate;
  }

  @Override
  public void onStart(@NotNull ExternalSystemTaskId id, String workingDir) {
    if (myDelegate != null) {
      myDelegate.onStart(id, workingDir);
    }
    else {
      onStart(id);
    }
  }

  /**
   * @deprecated use {@link #onStart(ExternalSystemTaskId, String)}
   */
  @Override
  @Deprecated(forRemoval = true)
  public void onStart(@NotNull ExternalSystemTaskId id) {
    if (myDelegate != null) {
      myDelegate.onStart(id);
    }
  }

  @Override
  public void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent event) {
    if (myDelegate != null) {
      myDelegate.onStatusChange(event);
    }
  }

  @Override
  public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) {
    if (myDelegate != null) {
      myDelegate.onTaskOutput(id, text, stdOut);
    }
  }

  @Override
  public void onEnd(@NotNull ExternalSystemTaskId id) {
    if (myDelegate != null) {
      myDelegate.onEnd(id);
    }
  }

  @Override
  public void onSuccess(@NotNull ExternalSystemTaskId id) {
    if (myDelegate != null) {
      myDelegate.onSuccess(id);
    }
  }

  @Override
  public void onFailure(@NotNull ExternalSystemTaskId id, @NotNull Exception e) {
    if (myDelegate != null) {
      myDelegate.onFailure(id, e);
    }
  }

  @Override
  public void beforeCancel(@NotNull ExternalSystemTaskId id) {
    if (myDelegate != null) {
      myDelegate.beforeCancel(id);
    }
  }

  @Override
  public void onCancel(@NotNull ExternalSystemTaskId id) {
    if (myDelegate != null) {
      myDelegate.onCancel(id);
    }
  }
}
