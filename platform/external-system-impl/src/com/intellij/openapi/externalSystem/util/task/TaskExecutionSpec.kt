// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util.task

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import org.jetbrains.annotations.ApiStatus;

/**
 * Spec for external system task execution.
 * @see [TaskExecutionSpec.create].
 */
@ApiStatus.NonExtendable
interface TaskExecutionSpec {

  /**
   * The project to associate the task with.
   * The project lifecycle specifies the lifecycle of the task.
   * Project disposal leads to the task cancellation.
   */
  val project: Project

  /**
   * Associated task parameters.
   */
  val settings: ExternalSystemTaskExecutionSettings

  /**
   * Specifies the type of [com.intellij.execution.Executor] to be used for the Task to run:
   * - [com.intellij.execution.executors.DefaultRunExecutor.EXECUTOR_ID],
   * - [com.intellij.execution.executors.DefaultDebugExecutor.EXECUTOR_ID],
   * - etc.
   * For more information see [com.intellij.execution.Executor].
   */
  val executorId: String

  /**
   * Identifier of the external system.
   */
  val systemId: ProjectSystemId

  /**
   * Defines how the task should be executed.
   * For more details see [ProgressExecutionMode] and [com.intellij.openapi.externalSystem.util.ExternalSystemTaskUnderProgress].
   */
  val progressExecutionMode: ProgressExecutionMode

  /**
   * Callback will be executed on task finish.
   * If operation return code is equal to 0, [TaskCallback.onSuccess] will be called, [TaskCallback.onFailure] otherwise.
   */
  val callback: TaskCallback?

  /**
   * Task execution listener with a full task lifecycle.
   */
  val listener: ExternalSystemTaskNotificationListener?

  /**
   * User data that will be used for task execution.
   */
  val userData: UserDataHolderBase?

  /**
   * Activate the tool window associated with the [executorId] before task run.
   */
  val activateToolWindowBeforeRun: Boolean

  /**
   * Activate and focus the tool window associated with the [executorId] on task failure.
   */
  val activateToolWindowOnFailure: Boolean

  companion object {
    @JvmStatic
    fun create(project: Project,
               systemId: ProjectSystemId,
               executorId: String,
               settings: ExternalSystemTaskExecutionSettings): TaskExecutionSpecBuilder {
      return TaskExecutionSpecBuilderImpl(project = project, systemId = systemId, executorId = executorId, settings = settings)
    }
  }
}