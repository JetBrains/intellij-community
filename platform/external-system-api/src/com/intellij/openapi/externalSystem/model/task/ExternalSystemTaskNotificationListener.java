// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model.task;

import com.intellij.execution.process.ProcessOutputType;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * Defines contract for callback to listen external task notifications.
 */
public interface ExternalSystemTaskNotificationListener extends EventListener {

  ExtensionPointName<ExternalSystemTaskNotificationListener> EP_NAME
    = ExtensionPointName.create("com.intellij.externalSystemTaskNotificationListener");

  @NotNull
  ExternalSystemTaskNotificationListener NULL_OBJECT = new ExternalSystemTaskNotificationListener() {};

  /**
   * Notifies that task with the given id is about to be started.
   */
  default void onStart(
    @NotNull String projectPath,
    @NotNull ExternalSystemTaskId id
  ) {
    onStart(id, projectPath);
  }

  /**
   * Notifies that task with the given id is finished successfully.
   */
  default void onSuccess(
    @NotNull String projectPath,
    @NotNull ExternalSystemTaskId id
  ) {
    onSuccess(id);
  }

  /**
   * Notifies that task with the given id is failed.
   */
  default void onFailure(
    @NotNull String projectPath,
    @NotNull ExternalSystemTaskId id,
    @NotNull Exception exception
  ) {
    onFailure(id, exception);
  }

  /**
   * Notifies that task with the given id is canceled.
   */
  default void onCancel(
    @NotNull String projectPath,
    @NotNull ExternalSystemTaskId id
  ) {
    onCancel(id);
  }

  /**
   * Notifies that task with the given id is finished.
   */
  default void onEnd(
    @NotNull String projectPath,
    @NotNull ExternalSystemTaskId id
  ) {
    onEnd(id);
  }

  /**
   * @deprecated use {@link #onStart(String, ExternalSystemTaskId)} instead
   */
  @Deprecated
  default void onStart(@NotNull ExternalSystemTaskId id, String workingDir) {
    onStart(id);
  }

  /**
   * @deprecated use {@link #onStart(String, ExternalSystemTaskId)} instead
   */
  @Deprecated
  default void onStart(@NotNull ExternalSystemTaskId id) { }

  default void onEnvironmentPrepared(@NotNull ExternalSystemTaskId id) { }

  /**
   * Notifies about processing state change of task with the given id.
   *
   * @param event event that holds information about processing change state of the
   *              {@link ExternalSystemTaskNotificationEvent#getId() target task}
   */
  default void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent event) { }

  /**
   * Notifies about the text written to stdout/stderr during the task execution
   *
   * @param id     id of the task being executed
   * @param text   text produced by the external system during the target task execution
   * @param outputType type of the output (stdout, stderr, or system)
   */
  default void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, @NotNull ProcessOutputType outputType) {}

  /**
   * @deprecated use {@link #onEnd(String, ExternalSystemTaskId)} instead
   */
  @Deprecated
  default void onEnd(@NotNull ExternalSystemTaskId id) { }

  /**
   * @deprecated use {@link #onSuccess(String, ExternalSystemTaskId)} instead
   */
  @Deprecated
  default void onSuccess(@NotNull ExternalSystemTaskId id) { }

  /**
   * @deprecated use {@link #onFailure(String, ExternalSystemTaskId, Exception)} instead
   */
  @Deprecated
  default void onFailure(@NotNull ExternalSystemTaskId id, @NotNull Exception e) { }

  /**
   * Notifies that task with the given id is scheduled for the cancellation.
   * <p/>
   * 'Scheduled' here means that intellij process-local codebase receives request to cancel the target task and even has not been
   * sent it to the Gradle process.
   *
   * @param id target task's id
   */
  default void beforeCancel(@NotNull ExternalSystemTaskId id) { }

  /**
   * @deprecated use {@link #onCancel(String, ExternalSystemTaskId)} instead
   */
  @Deprecated
  default void onCancel(@NotNull ExternalSystemTaskId id) { }
}
