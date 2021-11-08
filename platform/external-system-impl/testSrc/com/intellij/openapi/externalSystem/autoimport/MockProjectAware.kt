// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemRefreshStatus.SUCCESS
import com.intellij.openapi.externalSystem.autoimport.MockProjectAware.RefreshCollisionPassType.*
import com.intellij.openapi.observable.operations.AnonymousParallelOperationTrace
import com.intellij.openapi.observable.operations.AnonymousParallelOperationTrace.Companion.task
import com.intellij.openapi.observable.operations.onceAfterOperation
import com.intellij.openapi.observable.operations.subscribe
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.ConcurrencyUtil.once
import com.intellij.util.EventDispatcher
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

class MockProjectAware(
  override val projectId: ExternalSystemProjectId,
  private val project: Project,
  private val parentDisposable: Disposable
) : ExternalSystemProjectAware {

  val subscribeCounter = AtomicInteger(0)
  val unsubscribeCounter = AtomicInteger(0)
  val settingsAccessCounter = AtomicInteger(0)
  val refreshCounter = AtomicInteger(0)

  val refreshCollisionPassType = AtomicReference(DUPLICATE)
  val refreshStatus = AtomicReference(SUCCESS)

  private val eventDispatcher = EventDispatcher.create(Listener::class.java)
  private val refresh = AnonymousParallelOperationTrace(debugName = "$projectId MockProjectAware.refreshProject")

  private val _settingsFiles = LinkedHashSet<String>()
  override val settingsFiles: Set<String>
    get() = _settingsFiles.toSet().also {
      settingsAccessCounter.incrementAndGet()
    }

  fun resetAssertionCounters() {
    settingsAccessCounter.set(0)
    refreshCounter.set(0)
    subscribeCounter.set(0)
    unsubscribeCounter.set(0)
  }

  fun registerSettingsFile(path: String) {
    _settingsFiles.add(path)
  }

  override fun subscribe(listener: ExternalSystemProjectListener, parentDisposable: Disposable) {
    eventDispatcher.addListener(Listener.create(listener), parentDisposable)
    subscribeCounter.incrementAndGet()
    Disposer.register(parentDisposable, Disposable { unsubscribeCounter.incrementAndGet() })
  }

  fun forceReloadProject() {
    val message = "Useless assertion parameter: don't assert mock reload context"
    reloadProject(object : ExternalSystemProjectReloadContext {
      override val isExplicitReload get() = throw UnsupportedOperationException(message)
      override val hasUndefinedModifications get() = throw UnsupportedOperationException(message)
      override val settingsFilesContext get() = throw UnsupportedOperationException(message)
    })
  }

  fun notifySettingsFilesListChanged() = background { eventDispatcher.multicaster.onSettingsFilesListChange() }

  override fun reloadProject(context: ExternalSystemProjectReloadContext) {
    when (refreshCollisionPassType.get()!!) {
      DUPLICATE -> {
        doRefreshProject(context)
      }
      CANCEL -> {
        val task = once { doRefreshProject(context) }
        refresh.onceAfterOperation({ task.run() }, parentDisposable)
        if (refresh.isOperationCompleted()) task.run()
      }
      IGNORE -> {
        if (refresh.isOperationCompleted()) {
          doRefreshProject(context)
        }
      }
    }
  }

  private fun doRefreshProject(context: ExternalSystemProjectReloadContext) {
    background {
      val refreshStatus = refreshStatus.get()
      eventDispatcher.multicaster.onProjectReloadStart()
      refresh.task {
        refreshCounter.incrementAndGet()
        eventDispatcher.multicaster.insideProjectRefresh(context)
      }
      eventDispatcher.multicaster.onProjectReloadFinish(refreshStatus)
    }
  }

  private fun background(action: () -> Unit) {
    val projectTracker = AutoImportProjectTracker.getInstance(project)
    if (projectTracker.isAsyncChangesProcessing) {
      thread(block = action)
    }
    else {
      action()
    }
  }

  fun onceBeforeRefresh(action: () -> Unit) {
    beforeRefresh(times = 1, action)
  }

  fun beforeRefresh(times: Int, action: () -> Unit) {
    subscribe(times, action, ::beforeRefresh, parentDisposable)
  }

  fun beforeRefresh(action: () -> Unit, parentDisposable: Disposable) {
    eventDispatcher.addListener(object : Listener {
      override fun onProjectReloadStart() = action()
    }, parentDisposable)
  }

  fun onceDuringRefresh(action: (ExternalSystemProjectReloadContext) -> Unit) {
    duringRefresh(times = 1, action)
  }

  fun duringRefresh(times: Int, action: (ExternalSystemProjectReloadContext) -> Unit) {
    subscribe(times, action, ::duringRefresh, parentDisposable)
  }

  fun duringRefresh(action: (ExternalSystemProjectReloadContext) -> Unit, parentDisposable: Disposable) {
    eventDispatcher.addListener(object : Listener {
      override fun insideProjectRefresh(context: ExternalSystemProjectReloadContext) = action(context)
    }, parentDisposable)
  }

  fun onceAfterRefresh(action: (ExternalSystemRefreshStatus) -> Unit) {
    afterRefresh(times = 1, action)
  }

  fun afterRefresh(times: Int, action: (ExternalSystemRefreshStatus) -> Unit) {
    subscribe(times, action, ::afterRefresh, parentDisposable)
  }

  fun afterRefresh(action: (ExternalSystemRefreshStatus) -> Unit, parentDisposable: Disposable) {
    eventDispatcher.addListener(object : Listener {
      override fun onProjectReloadFinish(status: ExternalSystemRefreshStatus) = action(status)
    }, parentDisposable)
  }

  interface Listener : ExternalSystemProjectListener, EventListener {
    fun insideProjectRefresh(context: ExternalSystemProjectReloadContext) {}

    companion object {
      fun create(listener: ExternalSystemProjectListener) = object : Listener {
        override fun onProjectReloadStart() = listener.onProjectReloadStart()
        override fun onProjectReloadFinish(status: ExternalSystemRefreshStatus) = listener.onProjectReloadFinish(status)
        override fun onSettingsFilesListChange() = listener.onSettingsFilesListChange()
      }
    }
  }

  enum class RefreshCollisionPassType { DUPLICATE, CANCEL, IGNORE }
}