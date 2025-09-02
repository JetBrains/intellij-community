// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util.task

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture

@ApiStatus.Experimental
object TaskExecutionUtil {

  suspend fun runTask(spec: TaskExecutionSpecBuilder) {
    runTask(spec.build())
  }

  suspend fun runTask(spec: TaskExecutionSpec) {
    val future = CompletableFuture<Nothing?>()

    val listener = object : ExternalSystemTaskNotificationListenerAdapter(spec.listener) {
      override fun onSuccess(projectPath: String, id: ExternalSystemTaskId) {
        super.onSuccess(projectPath, id)
        future.complete(null)
      }

      override fun onCancel(projectPath: String, id: ExternalSystemTaskId) {
        super.onCancel(projectPath, id)
        future.cancel(true)
      }

      override fun onFailure(projectPath: String, id: ExternalSystemTaskId, exception: Exception) {
        super.onFailure(projectPath, id, exception)
        future.completeExceptionally(exception)
      }
    }

    ExternalSystemUtil.runTask(TaskExecutionSpecImpl(
      listener = listener,

      project = spec.project,
      systemId = spec.systemId,
      executorId = spec.executorId,
      settings = spec.settings,
      progressExecutionMode = spec.progressExecutionMode,
      callback = spec.callback,
      userData = spec.userData,
      activateToolWindowBeforeRun = spec.activateToolWindowBeforeRun,
      activateToolWindowOnFailure = spec.activateToolWindowOnFailure
    ))

    withContext(NonCancellable) {
      future.await()
    }
  }
}