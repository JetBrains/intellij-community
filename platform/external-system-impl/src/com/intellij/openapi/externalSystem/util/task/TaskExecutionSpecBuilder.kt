// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util.task

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CheckReturnValue

/**
 * Builder for [com.intellij.openapi.externalSystem.util.task.TaskExecutionSpec].
 * @see [TaskExecutionSpec.create].
 */
@ApiStatus.NonExtendable
interface TaskExecutionSpecBuilder {

  val project: Project
  val systemId: ProjectSystemId
  val executorId: String
  val settings: ExternalSystemTaskExecutionSettings

  @CheckReturnValue
  fun withProgressExecutionMode(progressExecutionMode: ProgressExecutionMode): TaskExecutionSpecBuilder

  @CheckReturnValue
  fun withCallback(callback: TaskCallback?): TaskExecutionSpecBuilder

  @CheckReturnValue
  fun withListener(listener: ExternalSystemTaskNotificationListener?): TaskExecutionSpecBuilder

  @CheckReturnValue
  fun withUserData(userData: UserDataHolderBase?): TaskExecutionSpecBuilder

  @CheckReturnValue
  fun withActivateToolWindowBeforeRun(activateToolWindowBeforeRun: Boolean): TaskExecutionSpecBuilder

  @CheckReturnValue
  fun withActivateToolWindowOnFailure(activateToolWindowOnFailure: Boolean): TaskExecutionSpecBuilder

  fun build(): TaskExecutionSpec

}
