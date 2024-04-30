// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.util.task

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemExecuteTaskTask
import com.intellij.openapi.externalSystem.service.internal.ExternalSystemProcessingManager
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.UserDataHolder
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.*

@Internal
class FilteringTaskNotificationListener(
  private val taskIdentifier: String,
  private val delegate: ExternalSystemTaskNotificationListener
) : ExternalSystemTaskNotificationListener {

  private val myTaskIdRef = Ref<ExternalSystemTaskId>()

  override fun onStart(id: ExternalSystemTaskId, workingDir: String?) {
    if (id.type != ExternalSystemTaskType.EXECUTE_TASK) {
      return
    }
    val manager = ExternalSystemProcessingManager.getInstance()
    val task = manager.findTask(id)
    if (task is ExternalSystemExecuteTaskTask) {
      val currentTaskIdentifier = task.getUserData(EXTERNAL_SYSTEM_TASK_IDENTIFIER_KEY)
      if (taskIdentifier == currentTaskIdentifier) {
        myTaskIdRef.set(id)
        delegate.onStart(id, workingDir)
      }
    }
  }

  override fun onEnvironmentPrepared(id: ExternalSystemTaskId) {
    if (shouldHandle(id)) {
      delegate.onEnvironmentPrepared(id)
    }
  }

  override fun onStatusChange(event: ExternalSystemTaskNotificationEvent) {
    if (shouldHandle(event.id)) {
      delegate.onStatusChange(event)
    }
  }

  override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {
    if (shouldHandle(id)) {
      delegate.onTaskOutput(id, text, stdOut)
    }
  }

  override fun onEnd(id: ExternalSystemTaskId) {
    if (shouldHandle(id)) {
      ExternalSystemProgressNotificationManager.getInstance().removeNotificationListener(this)
      delegate.onEnd(id)
    }
  }

  override fun onSuccess(id: ExternalSystemTaskId) {
    if (shouldHandle(id)) {
      delegate.onSuccess(id)
    }
  }

  override fun onFailure(id: ExternalSystemTaskId, e: Exception) {
    if (shouldHandle(id)) {
      delegate.onFailure(id, e)
    }
  }

  override fun beforeCancel(id: ExternalSystemTaskId) {
    if (shouldHandle(id)) {
      delegate.beforeCancel(id)
    }
  }

  override fun onCancel(id: ExternalSystemTaskId) {
    if (shouldHandle(id)) {
      delegate.onCancel(id)
    }
  }

  private fun shouldHandle(id: ExternalSystemTaskId): Boolean {
    return id == myTaskIdRef.get()
  }

  companion object {
    private val EXTERNAL_SYSTEM_TASK_IDENTIFIER_KEY: Key<String> = Key.create("EXTERNAL_SYSTEM_TASK_IDENTIFIER")

    @JvmStatic
    fun attach(project: Project, userDataHolder: UserDataHolder, delegate: ExternalSystemTaskNotificationListener) {
      val id = UUID.randomUUID().toString()
      userDataHolder.putUserData(EXTERNAL_SYSTEM_TASK_IDENTIFIER_KEY, id)
      val listener = FilteringTaskNotificationListener(id, delegate)
      ExternalSystemProgressNotificationManager.getInstance().addNotificationListener(listener, project)
    }
  }
}
