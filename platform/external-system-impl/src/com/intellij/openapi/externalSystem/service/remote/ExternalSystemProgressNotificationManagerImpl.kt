// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.remote

import com.intellij.execution.rmi.RemoteObject
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.service.notification.ExternalSystemProgressNotificationManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.EventDispatcher
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
class ExternalSystemProgressNotificationManagerImpl : RemoteObject(), ExternalSystemProgressNotificationManager, RemoteExternalSystemProgressNotificationManager {
  private val dispatcher = EventDispatcher.create(ExternalSystemTaskNotificationListener::class.java)

  override fun addNotificationListener(listener: ExternalSystemTaskNotificationListener): Boolean {
    return addListener(ALL_TASKS_KEY, listener)
  }

  override fun addNotificationListener(listener: ExternalSystemTaskNotificationListener, parentDisposable: Disposable): Boolean {
    return addListener(ALL_TASKS_KEY, listener, parentDisposable)
  }

  override fun addNotificationListener(taskId: ExternalSystemTaskId, listener: ExternalSystemTaskNotificationListener): Boolean {
    return addListener(taskId, listener)
  }

  override fun removeNotificationListener(listener: ExternalSystemTaskNotificationListener): Boolean {
    val toRemove = dispatcher.listeners.filter { (it as TaskListenerWrapper).delegate === listener }
    dispatcher.listeners.removeAll(toRemove)
    return toRemove.isNotEmpty()
  }

  override fun onStart(projectPath: String, id: ExternalSystemTaskId) {
    forEachListener { it.onStart(projectPath, id) }
  }

  override fun onEnvironmentPrepared(id: ExternalSystemTaskId) {
    forEachListener { it.onEnvironmentPrepared(id) }
  }

  override fun onStatusChange(event: ExternalSystemTaskNotificationEvent) {
    forEachListener { it.onStatusChange(event) }
  }

  override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {
    forEachListener { it.onTaskOutput(id, text, stdOut) }
  }

  override fun onEnd(projectPath: String, id: ExternalSystemTaskId) {
    try {
      forEachListener { it.onEnd(projectPath, id) }
    }
    finally {
      val toRemove = dispatcher.listeners.filter { (it as TaskListenerWrapper).taskId === id }
      dispatcher.listeners.removeAll(toRemove)
    }
  }

  override fun onSuccess(projectPath: String, id: ExternalSystemTaskId) {
    forEachListener { it.onSuccess(projectPath, id) }
  }

  override fun onFailure(projectPath: String, id: ExternalSystemTaskId, exception: Exception) {
    forEachListener { it.onFailure(projectPath, id, exception) }
  }

  override fun beforeCancel(id: ExternalSystemTaskId) {
    forEachListener { it.beforeCancel(id) }
  }

  override fun onCancel(projectPath: String, id: ExternalSystemTaskId) {
    forEachListener { it.onCancel(projectPath, id) }
  }

  private fun addListener(tasksKey: Any, listener: ExternalSystemTaskNotificationListener, parentDisposable: Disposable? = null): Boolean {
    val wrapper = TaskListenerWrapper(tasksKey, listener)
    if (dispatcher.listeners.contains(wrapper)) return false
    if (parentDisposable == null) {
      dispatcher.addListener(wrapper)
    }
    else {
      dispatcher.addListener(wrapper, parentDisposable)
    }
    return true
  }

  private fun forEachListener(action: (ExternalSystemTaskNotificationListener) -> Unit) {
    ProgressManager.getInstance().executeNonCancelableSection {
      LOG.runAndLogException {
        action.invoke(dispatcher.multicaster)
        ExternalSystemTaskNotificationListener.EP_NAME.forEachExtensionSafe(action::invoke)
      }
    }
  }

  @Suppress("SuspiciousEqualsCombination")
  private class TaskListenerWrapper(
    val taskId: Any,
    val delegate: ExternalSystemTaskNotificationListener
  ) : ExternalSystemTaskNotificationListener {
    override fun onSuccess(projectPath: String, id: ExternalSystemTaskId) {
      if (taskId !== ALL_TASKS_KEY && taskId != id) return
      delegate.onSuccess(projectPath, id)
    }

    override fun onFailure(projectPath: String, id: ExternalSystemTaskId, exception: Exception) {
      if (taskId !== ALL_TASKS_KEY && taskId != id) return
      delegate.onFailure(projectPath, id, exception)
    }

    override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {
      if (taskId !== ALL_TASKS_KEY && taskId != id) return
      delegate.onTaskOutput(id, text, stdOut)
    }

    override fun onStatusChange(event: ExternalSystemTaskNotificationEvent) {
      if (taskId !== ALL_TASKS_KEY && taskId != event.id) return
      delegate.onStatusChange(event)
    }

    override fun onCancel(projectPath: String, id: ExternalSystemTaskId) {
      if (taskId !== ALL_TASKS_KEY && taskId != id) return
      delegate.onCancel(projectPath, id)
    }

    override fun onEnd(projectPath: String, id: ExternalSystemTaskId) {
      if (taskId !== ALL_TASKS_KEY && taskId != id) return
      delegate.onEnd(projectPath, id)
    }

    override fun beforeCancel(id: ExternalSystemTaskId) {
      if (taskId !== ALL_TASKS_KEY && taskId != id) return
      delegate.beforeCancel(id)
    }

    override fun onStart(projectPath: String, id: ExternalSystemTaskId) {
      if (taskId !== ALL_TASKS_KEY && taskId != id) return
      delegate.onStart(projectPath, id)
    }

    override fun onEnvironmentPrepared(id: ExternalSystemTaskId) {
      if (taskId !== ALL_TASKS_KEY && taskId != id) return
      delegate.onEnvironmentPrepared(id)
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false
      other as TaskListenerWrapper
      if (taskId !== other.taskId) return false
      if (delegate !== other.delegate) return false
      return true
    }

    override fun hashCode(): Int {
      var result = taskId.hashCode()
      result = 31 * result + delegate.hashCode()
      return result
    }
  }

  companion object {
    private val LOG = logger<ExternalSystemProgressNotificationManager>()

    private val ALL_TASKS_KEY = Any()

    @JvmStatic
    fun getInstanceImpl(): ExternalSystemProgressNotificationManagerImpl {
      return ExternalSystemProgressNotificationManager.getInstance() as ExternalSystemProgressNotificationManagerImpl
    }

    @JvmStatic
    @TestOnly
    @ApiStatus.Internal
    fun getListeners(): Map<Any, List<ExternalSystemTaskNotificationListener>> {
      return getInstanceImpl().dispatcher.listeners.groupBy({ (it as TaskListenerWrapper).taskId },
                                                            { (it as TaskListenerWrapper).delegate })
    }

    @JvmStatic
    @TestOnly
    @ApiStatus.Internal
    fun assertListenersReleased() {
      assertListenersReleased(null, emptyMap())
    }

    @JvmStatic
    @TestOnly
    @ApiStatus.Internal
    fun assertListenersReleased(taskId: Any? = null, expected: Map<Any, List<ExternalSystemTaskNotificationListener>> = emptyMap()) {
      val listeners = getListeners()
      if (listeners == expected) return
      if (taskId == null && listeners.isNotEmpty()) {
        throw AssertionError("Leaked listeners: $listeners")
      }
      if (taskId != null && listeners.containsKey(taskId)) {
        throw AssertionError("Leaked listeners for task '$taskId': ${listeners[taskId]}")
      }
    }

    @JvmStatic
    @TestOnly
    @ApiStatus.Internal
    fun cleanupListeners() {
      getInstanceImpl().dispatcher.listeners.clear()
    }
  }
}