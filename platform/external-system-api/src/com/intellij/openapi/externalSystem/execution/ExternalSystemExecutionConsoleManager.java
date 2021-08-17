// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.execution;

import com.intellij.execution.filters.Filter;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputType;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ExecutionConsole;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTask;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent;
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * External system execution console integration that defines a bridge
 * between external task execution process and Intellij execution console
 * implementation. It is a main console configurator, that describes how
 * to create console, what entities are in process output and how to highlight
 * them, which console actions it should contain, etc.
 */
public interface ExternalSystemExecutionConsoleManager<EC extends ExecutionConsole, PH extends ProcessHandler> {
  ExtensionPointName<ExternalSystemExecutionConsoleManager<?, ?>> EP_NAME
    = ExtensionPointName.create("com.intellij.externalSystemExecutionConsoleManager");

  /**
   * External system id of corresponding owner build system.
   */
  @NotNull ProjectSystemId getExternalSystemId();

  /**
   * Creates and attaches execution console into task execution process.
   *
   * @param project        is project in context of which task is runned.
   * @param task           is task to which should be attached output console.
   * @param env            is execution environment of executing task.
   * @param processHandler is process handler of executing task.
   * @return attached execution console.
   */
  default @Nullable EC attachExecutionConsole(
    @NotNull Project project,
    @NotNull ExternalSystemTask task,
    @Nullable ExecutionEnvironment env,
    @Nullable PH processHandler
  ) { return null; }

  /**
   * Notifies about text written to stdout/stderr during the task execution.
   *
   * @param executionConsole  is console of executing task.
   * @param processHandler    is process handler of executing task.
   * @param text              is text that is produced by external system during the target task execution
   * @param processOutputType is output type of received message
   * @see ProcessOutputType#isStdout
   * @see ExternalSystemTaskNotificationListener#onTaskOutput
   */
  void onOutput(
    @NotNull EC executionConsole,
    @NotNull PH processHandler,
    @NotNull String text,
    @NotNull Key processOutputType
  );

  /**
   * Notifies about processing state change of task with the given id.
   *
   * @param executionConsole is console of executing task.
   * @param event            is container of event information about processing change state of the
   *                         {@link ExternalSystemTaskNotificationEvent#getId() target task}.
   * @see ExternalSystemTaskNotificationListener#onStatusChange
   */
  default void onStatusChange(
    @NotNull EC executionConsole,
    @NotNull ExternalSystemTaskNotificationEvent event
  ) { }

  /**
   * Checks that given task can be handled by this console manager.
   *
   * @param task is task to check.
   */
  boolean isApplicableFor(@NotNull ExternalSystemTask task);

  /**
   * Provides actions to restart execution task process witch handled by given console.
   *
   * @param consoleView is console into which restart actions will be placed.
   */
  AnAction[] getRestartActions(@NotNull EC consoleView);

  /**
   * Provides console filters that highlights and adds hyperlinks into console.
   *
   * @param project is project in context of which task is runned.
   * @param task    is task that execution output is handled by console into which provided filters will be applied.
   * @param env     is execution environment of executing task.
   */
  @ApiStatus.Experimental
  default Filter[] getCustomExecutionFilters(
    @NotNull Project project,
    @NotNull ExternalSystemTask task,
    @Nullable ExecutionEnvironment env
  ) { return Filter.EMPTY_ARRAY; }

  /**
   * Provides custom console actions.
   *
   * @param project is project in context of which task is runned.
   * @param task    is task that execution output is handled by console into which provided actions will be applied.
   * @param env     is execution environment of executing task.
   */
  @ApiStatus.Experimental
  default AnAction[] getCustomActions(
    @NotNull Project project,
    @NotNull ExternalSystemTask task,
    @Nullable ExecutionEnvironment env
  ) { return AnAction.EMPTY_ARRAY; }

  /**
   * Provides custom execution tree context actions.
   *
   * @param project is project in context of which task is runned.
   * @param task    is task that execution output is represented by build tree in console view.
   * @param env     is execution environment of executing task.
   * @see com.intellij.build.BuildTreeConsoleView
   */
  @ApiStatus.Experimental
  default AnAction[] getCustomContextActions(
    @NotNull Project project,
    @NotNull ExternalSystemTask task,
    @Nullable ExecutionEnvironment env
  ) { return AnAction.EMPTY_ARRAY; }
}
