// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util.task

import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture

@ApiStatus.Internal
class TaskExecutionSpecBuilderImpl : TaskExecutionSpecBuilder {

  override lateinit var project: Project
  override lateinit var systemId: ProjectSystemId
  override lateinit var settings: ExternalSystemTaskExecutionSettings
  override var executorId: String = DefaultRunExecutor.EXECUTOR_ID
  private var progressExecutionMode: ProgressExecutionMode = ProgressExecutionMode.IN_BACKGROUND_ASYNC
  private var callback: TaskCallback? = null
  private var listener: ExternalSystemTaskNotificationListener? = null
  private var userData: UserDataHolderBase? = null
  private var activateToolWindowBeforeRun: Boolean = false
  private var activateToolWindowOnFailure: Boolean = true

  override fun withProject(project: Project): TaskExecutionSpecBuilder = apply {
    this.project = project
  }

  override fun withSystemId(systemId: ProjectSystemId): TaskExecutionSpecBuilder = apply {
    this.systemId = systemId
  }

  override fun withSettings(settings: ExternalSystemTaskExecutionSettings): TaskExecutionSpecBuilder = apply {
    this.settings = settings
  }

  override fun withExecutorId(executorId: String): TaskExecutionSpecBuilder = apply {
    this.executorId = executorId
  }

  override fun withProgressExecutionMode(progressExecutionMode: ProgressExecutionMode): TaskExecutionSpecBuilder {
    this.progressExecutionMode = progressExecutionMode
    return this
  }

  override fun withCallback(callback: TaskCallback?): TaskExecutionSpecBuilder {
    this.callback = callback
    return this
  }

  override fun withCallback(future: CompletableFuture<Boolean>): TaskExecutionSpecBuilder = apply {
    this.callback = object : TaskCallback {
      override fun onSuccess() { future.complete(true) }
      override fun onFailure() { future.complete(false) }
    }
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