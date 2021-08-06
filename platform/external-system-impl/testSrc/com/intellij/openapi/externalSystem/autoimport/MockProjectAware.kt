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
  private val project: Project
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

  override fun subscribe(listener: ExternalSystemProjectRefreshListener, parentDisposable: Disposable) {
    eventDispatcher.addListener(Listener.create(listener), parentDisposable)
    subscribeCounter.incrementAndGet()
    Disposer.register(parentDisposable, Disposable { unsubscribeCounter.incrementAndGet() })
  }

  override fun reloadProject(context: ExternalSystemProjectReloadContext) {
    when (refreshCollisionPassType.get()!!) {
      DUPLICATE -> {
        doRefreshProject(context)
      }
      CANCEL -> {
        val task = once { doRefreshProject(context) }
        refresh.onceAfterOperation { task.run() }
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
      eventDispatcher.multicaster.beforeProjectRefresh()
      refresh.task {
        refreshCounter.incrementAndGet()
        eventDispatcher.multicaster.insideProjectRefresh(context)
      }
      eventDispatcher.multicaster.afterProjectRefresh(refreshStatus)
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
    subscribe(times, action, {
      object : Listener {
        override fun beforeProjectRefresh() = it()
      }
    }, eventDispatcher::addListener)
  }

  fun onceDuringRefresh(action: (ExternalSystemProjectReloadContext) -> Unit) {
    duringRefresh(times = 1, action)
  }

  fun duringRefresh(times: Int, action: (ExternalSystemProjectReloadContext) -> Unit) {
    subscribe(times, action, {
      object : Listener {
        override fun insideProjectRefresh(context: ExternalSystemProjectReloadContext) = it(context)
      }
    }, eventDispatcher::addListener)
  }

  fun onceAfterRefresh(action: (ExternalSystemRefreshStatus) -> Unit) {
    afterRefresh(times = 1, action)
  }

  fun afterRefresh(times: Int, action: (ExternalSystemRefreshStatus) -> Unit) {
    subscribe(times, action, {
      object : Listener {
        override fun afterProjectRefresh(status: ExternalSystemRefreshStatus) = it(status)
      }
    }, eventDispatcher::addListener)
  }

  interface Listener : ExternalSystemProjectRefreshListener, EventListener {
    fun insideProjectRefresh(context: ExternalSystemProjectReloadContext) {}

    companion object {
      fun create(listener: ExternalSystemProjectRefreshListener) = object : Listener {
        override fun beforeProjectRefresh() = listener.beforeProjectRefresh()
        override fun afterProjectRefresh(status: ExternalSystemRefreshStatus) = listener.afterProjectRefresh(status)
      }
    }
  }

  enum class RefreshCollisionPassType { DUPLICATE, CANCEL, IGNORE }
}