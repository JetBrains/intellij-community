// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model.task;

import com.intellij.execution.process.ProcessOutputType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ExternalSystemTaskNotificationListenerAdapter implements ExternalSystemTaskNotificationListener {

  private final @Nullable ExternalSystemTaskNotificationListener myDelegate;

  /**
   * @deprecated Please use the {@link ExternalSystemTaskNotificationListener} directly.
   */
  @Deprecated(forRemoval = true)
  public ExternalSystemTaskNotificationListenerAdapter() {
    this(null);
  }

  public ExternalSystemTaskNotificationListenerAdapter(@Nullable ExternalSystemTaskNotificationListener delegate) {
    myDelegate = delegate;
  }

  @Override
  public void onStart(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {
    if (myDelegate != null) {
      myDelegate.onStart(projectPath, id);
    }
    else {
      ExternalSystemTaskNotificationListener.super.onStart(projectPath, id);
    }
  }

  @Override
  @SuppressWarnings("deprecation")
  public void onStart(@NotNull ExternalSystemTaskId id, @NotNull String workingDir) {
    if (myDelegate != null) {
      myDelegate.onStart(id, workingDir);
    }
    else {
      ExternalSystemTaskNotificationListener.super.onStart(id, workingDir);
    }
  }

  @Override
  @SuppressWarnings("deprecation")
  public void onStart(@NotNull ExternalSystemTaskId id) {
    if (myDelegate != null) {
      myDelegate.onStart(id);
    }
    else {
      ExternalSystemTaskNotificationListener.super.onStart(id);
    }
  }

  @Override
  public void onEnvironmentPrepared(@NotNull ExternalSystemTaskId id) {
    if (myDelegate != null) {
      myDelegate.onEnvironmentPrepared(id);
    }
  }

  @Override
  public void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent event) {
    if (myDelegate != null) {
      myDelegate.onStatusChange(event);
    }
  }

  @Override
  public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, @NotNull ProcessOutputType processOutputType) {
    if (myDelegate != null) {
      myDelegate.onTaskOutput(id, text, processOutputType);
    }
  }

  @Override
  public void onEnd(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {
    if (myDelegate != null) {
      myDelegate.onEnd(projectPath, id);
    }
    else {
      ExternalSystemTaskNotificationListener.super.onEnd(projectPath, id);
    }
  }

  @Override
  @SuppressWarnings("deprecation")
  public void onEnd(@NotNull ExternalSystemTaskId id) {
    if (myDelegate != null) {
      myDelegate.onEnd(id);
    }
    else {
      ExternalSystemTaskNotificationListener.super.onEnd(id);
    }
  }

  @Override
  public void onSuccess(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {
    if (myDelegate != null) {
      myDelegate.onSuccess(projectPath, id);
    }
    else {
      ExternalSystemTaskNotificationListener.super.onSuccess(projectPath, id);
    }
  }

  @Override
  @SuppressWarnings("deprecation")
  public void onSuccess(@NotNull ExternalSystemTaskId id) {
    if (myDelegate != null) {
      myDelegate.onSuccess(id);
    }
    else {
      ExternalSystemTaskNotificationListener.super.onSuccess(id);
    }
  }

  @Override
  public void onFailure(@NotNull String projectPath, @NotNull ExternalSystemTaskId id, @NotNull Exception exception) {
    if (myDelegate != null) {
      myDelegate.onFailure(projectPath, id, exception);
    }
    else {
      ExternalSystemTaskNotificationListener.super.onFailure(projectPath, id, exception);
    }
  }

  @Override
  @SuppressWarnings("deprecation")
  public void onFailure(@NotNull ExternalSystemTaskId id, @NotNull Exception exception) {
    if (myDelegate != null) {
      myDelegate.onFailure(id, exception);
    }
    else {
      ExternalSystemTaskNotificationListener.super.onFailure(id, exception);
    }
  }

  @Override
  public void beforeCancel(@NotNull ExternalSystemTaskId id) {
    if (myDelegate != null) {
      myDelegate.beforeCancel(id);
    }
  }

  @Override
  public void onCancel(@NotNull String projectPath, @NotNull ExternalSystemTaskId id) {
    if (myDelegate != null) {
      myDelegate.onCancel(projectPath, id);
    }
    else {
      ExternalSystemTaskNotificationListener.super.onCancel(projectPath, id);
    }
  }

  @Override
  @SuppressWarnings("deprecation")
  public void onCancel(@NotNull ExternalSystemTaskId id) {
    if (myDelegate != null) {
      myDelegate.onCancel(id);
    }
    else {
      ExternalSystemTaskNotificationListener.super.onCancel(id);
    }
  }
}
