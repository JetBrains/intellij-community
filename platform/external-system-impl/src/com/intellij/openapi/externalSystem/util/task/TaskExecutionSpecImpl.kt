// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util.task

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.model.execution.ExternalSystemTaskExecutionSettings
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.execution.ProgressExecutionMode
import com.intellij.openapi.externalSystem.task.TaskCallback
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class TaskExecutionSpecImpl(
  override val project: Project,
  override val systemId: ProjectSystemId,
  override val executorId: String,
  override val settings: ExternalSystemTaskExecutionSettings,
  override val progressExecutionMode: ProgressExecutionMode,
  override val callback: TaskCallback?,
  override val listener: ExternalSystemTaskNotificationListener?,
  override val userData: UserDataHolderBase?,
  override val activateToolWindowBeforeRun: Boolean,
  override val activateToolWindowOnFailure: Boolean,
) : TaskExecutionSpec