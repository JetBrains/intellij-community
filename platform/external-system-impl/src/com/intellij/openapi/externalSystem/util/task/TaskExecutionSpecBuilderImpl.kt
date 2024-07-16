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

@ApiStatus.Internal
class TaskExecutionSpecBuilderImpl(
  override val project: Project,
  override val systemId: ProjectSystemId,
  override val executorId: String,
  override val settings: ExternalSystemTaskExecutionSettings,
  var progressExecutionMode: ProgressExecutionMode = ProgressExecutionMode.IN_BACKGROUND_ASYNC,
  var callback: TaskCallback? = null,
  var listener: ExternalSystemTaskNotificationListener? = null,
  var userData: UserDataHolderBase? = null,
  var activateToolWindowBeforeRun: Boolean = false,
  var activateToolWindowOnFailure: Boolean = true
) : TaskExecutionSpecBuilder {

  override fun withProgressExecutionMode(progressExecutionMode: ProgressExecutionMode): TaskExecutionSpecBuilder {
    this.progressExecutionMode = progressExecutionMode
    return this
  }

  override fun withCallback(callback: TaskCallback?): TaskExecutionSpecBuilder {
    this.callback = callback
    return this
  }

  override fun withListener(listener: ExternalSystemTaskNotificationListener?): TaskExecutionSpecBuilder {
    this.listener = listener
    return this
  }

  override fun withUserData(userData: UserDataHolderBase?): TaskExecutionSpecBuilder {
    this.userData = userData
    return this
  }

  override fun withActivateToolWindowBeforeRun(activateToolWindowBeforeRun: Boolean): TaskExecutionSpecBuilder {
    this.activateToolWindowBeforeRun = activateToolWindowBeforeRun
    return this
  }

  override fun withActivateToolWindowOnFailure(activateToolWindowOnFailure: Boolean): TaskExecutionSpecBuilder {
    this.activateToolWindowOnFailure = activateToolWindowOnFailure
    return this
  }

  override fun build(): TaskExecutionSpec {
    return TaskExecutionSpecImpl(
      project = project,
      systemId = systemId,
      executorId = executorId,
      settings = settings,
      progressExecutionMode = progressExecutionMode,
      callback = callback,
      listener = listener,
      userData = userData,
      activateToolWindowBeforeRun = activateToolWindowBeforeRun,
      activateToolWindowOnFailure = activateToolWindowOnFailure
    )
  }
}