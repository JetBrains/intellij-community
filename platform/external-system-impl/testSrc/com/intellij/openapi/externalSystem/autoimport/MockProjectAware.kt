// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemRefreshStatus.SUCCESS
import com.intellij.openapi.externalSystem.autoimport.MockProjectAware.ReloadCollisionPassType.*
import com.intellij.openapi.observable.operations.AnonymousParallelOperationTrace
import com.intellij.openapi.observable.operations.AnonymousParallelOperationTrace.Companion.task
import com.intellij.openapi.observable.operations.onceAfterOperation
import com.intellij.openapi.observable.operations.subscribe
import com.intellij.openapi.util.Disposer
import com.intellij.util.ConcurrencyUtil.once
import com.intellij.util.EventDispatcher
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.collections.LinkedHashMap
import kotlin.concurrent.thread

class MockProjectAware(
  override val projectId: ExternalSystemProjectId,
  private val parentDisposable: Disposable
) : ExternalSystemProjectAware {

  val subscribeCounter = AtomicInteger(0)
  val unsubscribeCounter = AtomicInteger(0)
  val settingsAccessCounter = AtomicInteger(0)
  val reloadCounter = AtomicInteger(0)

  val reloadCollisionPassType = AtomicReference(DUPLICATE)
  val reloadStatus = AtomicReference(SUCCESS)

  private val eventDispatcher = EventDispatcher.create(Listener::class.java)
  private val reloadProject = AnonymousParallelOperationTrace(debugName = "$projectId MockProjectAware.reloadProject")

  private val _settingsFiles = LinkedHashSet<String>()
  override val settingsFiles: Set<String>
    get() = _settingsFiles.toSet().also {
      settingsAccessCounter.incrementAndGet()
    }

  private val ignoredSettingsFiles = LinkedHashMap<String, (ExternalSystemSettingsFilesModificationContext) -> Boolean>()

  fun resetAssertionCounters() {
    settingsAccessCounter.set(0)
    reloadCounter.set(0)
    subscribeCounter.set(0)
    unsubscribeCounter.set(0)
  }

  fun registerSettingsFile(path: String) {
    _settingsFiles.add(path)
  }

  fun ignoreSettingsFileWhen(path: String, condition: (ExternalSystemSettingsFilesModificationContext) -> Boolean) {
    ignoredSettingsFiles[path] = condition
  }

  override fun isIgnoredSettingsFileEvent(path: String, context: ExternalSystemSettingsFilesModificationContext): Boolean {
    val condition = ignoredSettingsFiles[path]
    if (condition != null) {
      return condition(context)
    }
    else {
      return super.isIgnoredSettingsFileEvent(path, context)
    }
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

  fun fireSettingsFilesListChanged() = background { eventDispatcher.multicaster.onSettingsFilesListChange() }

  override fun reloadProject(context: ExternalSystemProjectReloadContext) {
    when (reloadCollisionPassType.get()!!) {
      DUPLICATE -> {
        reloadProjectImpl(context)
      }
      CANCEL -> {
        val task = once { reloadProjectImpl(context) }
        reloadProject.onceAfterOperation({ task.run() }, parentDisposable)
        if (reloadProject.isOperationCompleted()) task.run()
      }
      IGNORE -> {
        if (reloadProject.isOperationCompleted()) {
          reloadProjectImpl(context)
        }
      }
    }
  }

  private fun reloadProjectImpl(context: ExternalSystemProjectReloadContext) {
    background {
      val reloadStatus = reloadStatus.get()
      eventDispatcher.multicaster.onProjectReloadStart()
      reloadProject.task {
        reloadCounter.incrementAndGet()
        eventDispatcher.multicaster.insideProjectRefresh(context)
      }
      eventDispatcher.multicaster.onProjectReloadFinish(reloadStatus)
    }
  }

  private fun background(action: () -> Unit) {
    if (AutoImportProjectTracker.isAsyncChangesProcessing) {
      thread(block = action)
    }
    else {
      action()
    }
  }

  fun onceWhenReloadStarted(action: () -> Unit) {
    whenReloadStarted(times = 1, action)
  }

  fun whenReloadStarted(times: Int, action: () -> Unit) {
    subscribe(times, action, ::whenReloadStarted, parentDisposable)
  }

  fun whenReloadStarted(action: () -> Unit, parentDisposable: Disposable) {
    eventDispatcher.addListener(object : Listener {
      override fun onProjectReloadStart() = action()
    }, parentDisposable)
  }

  fun onceWhenReloading(action: (ExternalSystemProjectReloadContext) -> Unit) {
    whenReloading(times = 1, action)
  }

  fun whenReloading(times: Int, action: (ExternalSystemProjectReloadContext) -> Unit) {
    subscribe(times, action, ::whenReloading, parentDisposable)
  }

  fun whenReloading(action: (ExternalSystemProjectReloadContext) -> Unit, parentDisposable: Disposable) {
    eventDispatcher.addListener(object : Listener {
      override fun insideProjectRefresh(context: ExternalSystemProjectReloadContext) = action(context)
    }, parentDisposable)
  }

  fun onceWhenReloadFinished(action: (ExternalSystemRefreshStatus) -> Unit) {
    whenReloadFinished(times = 1, action)
  }

  fun whenReloadFinished(times: Int, action: (ExternalSystemRefreshStatus) -> Unit) {
    subscribe(times, action, ::whenReloadFinished, parentDisposable)
  }

  fun whenReloadFinished(action: (ExternalSystemRefreshStatus) -> Unit, parentDisposable: Disposable) {
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

  enum class ReloadCollisionPassType { DUPLICATE, CANCEL, IGNORE }
}