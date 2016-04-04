package com.intellij.openapi.externalSystem.model.task;

import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 11/10/11 12:18 PM
 */
public abstract class ExternalSystemTaskNotificationListenerAdapter implements ExternalSystemTaskNotificationListener {

  @NotNull public static final ExternalSystemTaskNotificationListener NULL_OBJECT = new ExternalSystemTaskNotificationListenerAdapter() { };

  @Override
  public void onQueued(@NotNull ExternalSystemTaskId id, String workingDir) {
  }

  @Override
  public void onStart(@NotNull ExternalSystemTaskId id) {
  }

  @Override
  public void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent event) {
  }

  @Override
  public void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut) {
  }

  @Override
  public void onEnd(@NotNull ExternalSystemTaskId id) {
  }

  @Override
  public void onSuccess(@NotNull ExternalSystemTaskId id) {
  }

  @Override
  public void onFailure(@NotNull ExternalSystemTaskId id, @NotNull Exception e) {
  }

  @Override
  public void beforeCancel(@NotNull ExternalSystemTaskId id) {
  }

  @Override
  public void onCancel(@NotNull ExternalSystemTaskId id) {
  }
}
