package com.intellij.openapi.externalSystem.model.task;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

/**
 * Defines contract for callback to listen external task notifications.
 *
 * @author Denis Zhdanov
 * @since 11/10/11 11:57 AM
 */
public interface ExternalSystemTaskNotificationListener {

  ExtensionPointName<ExternalSystemTaskNotificationListener> EP_NAME
    = ExtensionPointName.create("com.intellij.externalSystemTaskNotificationListener");

  /**
   * @deprecated use {@link #onStart(ExternalSystemTaskId, String)}
   */
  void onQueued(@NotNull ExternalSystemTaskId id, String workingDir);

  /**
   * Notifies that task with the given id is about to be started.
   *
   * @param id         target task's id
   * @param workingDir working directory
   */
  default void onStart(@NotNull ExternalSystemTaskId id, String workingDir) {
    onQueued(id, workingDir);
    onStart(id);
  }

  /**
   * @deprecated use {@link #onStart(ExternalSystemTaskId, String)}
   */
  void onStart(@NotNull ExternalSystemTaskId id);

  /**
   * Notifies about processing state change of task with the given id.
   *
   * @param event event that holds information about processing change state of the
   *              {@link ExternalSystemTaskNotificationEvent#getId() target task}
   */
  void onStatusChange(@NotNull ExternalSystemTaskNotificationEvent event);

  /**
   * Notifies about text written to stdout/stderr during the task execution
   *
   * @param id     id of the task being executed
   * @param text   text produced by external system during the target task execution
   * @param stdOut flag which identifies output type (stdout or stderr)
   */
  void onTaskOutput(@NotNull ExternalSystemTaskId id, @NotNull String text, boolean stdOut);

  /**
   * Notifies that task with the given id is finished.
   *
   * @param id target task's id
   */
  void onEnd(@NotNull ExternalSystemTaskId id);

  /**
   * Notifies that task with the given id is finished successfully.
   *
   * @param id target task's id
   */
  void onSuccess(@NotNull ExternalSystemTaskId id);

  /**
   * Notifies that task with the given id is failed.
   *
   * @param id target task's id
   * @param e  failure exception
   */
  void onFailure(@NotNull ExternalSystemTaskId id, @NotNull Exception e);

  /**
   * Notifies that task with the given id is queued for the cancellation.
   * <p/>
   * 'Queued' here means that intellij process-local codebase receives request to cancel the target task and even has not been
   * sent it to the slave gradle api process.
   *
   * @param id target task's id
   */
  void beforeCancel(@NotNull ExternalSystemTaskId id);

  /**
   * Notifies that task with the given id is cancelled.
   *
   * @param id target task's id
   */
  void onCancel(@NotNull ExternalSystemTaskId id);
}
