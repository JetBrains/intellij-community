// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemRefreshStatus.SUCCESS
import com.intellij.openapi.externalSystem.autoimport.MockProjectAware.ReloadCollisionPassType.*
import com.intellij.openapi.observable.dispatcher.SingleEventDispatcher
import com.intellij.openapi.observable.operation.core.AtomicOperationTrace
import com.intellij.openapi.observable.operation.core.isOperationInProgress
import com.intellij.openapi.observable.operation.core.traceRun
import com.intellij.openapi.observable.operation.core.withCompletedOperation
import com.intellij.openapi.util.Disposer
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

  private val reloadProject = AtomicOperationTrace(name = "$projectId MockProjectAware.reloadProject")

  val startReloadEventDispatcher = SingleEventDispatcher.create()
  val reloadEventDispatcher = SingleEventDispatcher.create<ExternalSystemProjectReloadContext>()
  val finishReloadEventDispatcher = SingleEventDispatcher.create<ExternalSystemRefreshStatus>()
  private val settingsChangeEventDispatcher = SingleEventDispatcher.create()

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
    startReloadEventDispatcher.whenEventHappened(parentDisposable, listener::onProjectReloadStart)
    finishReloadEventDispatcher.whenEventHappened(parentDisposable, listener::onProjectReloadFinish)
    settingsChangeEventDispatcher.whenEventHappened(parentDisposable, listener::onSettingsFilesListChange)
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

  fun fireSettingsFilesListChanged() {
    background {
      settingsChangeEventDispatcher.fireEvent()
    }
  }

  override fun reloadProject(context: ExternalSystemProjectReloadContext) {
    when (reloadCollisionPassType.get()!!) {
      DUPLICATE -> {
        reloadProjectImpl(context)
      }
      CANCEL -> {
        reloadProject.withCompletedOperation(parentDisposable) {
          reloadProjectImpl(context)
        }
      }
      IGNORE -> {
        if (!reloadProject.isOperationInProgress()) {
          reloadProjectImpl(context)
        }
      }
    }
  }

  private fun reloadProjectImpl(context: ExternalSystemProjectReloadContext) {
    background {
      val reloadStatus = reloadStatus.get()
      startReloadEventDispatcher.fireEvent()
      reloadProject.traceRun {
        reloadCounter.incrementAndGet()
        reloadEventDispatcher.fireEvent(context)
      }
      finishReloadEventDispatcher.fireEvent(reloadStatus)
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

  enum class ReloadCollisionPassType { DUPLICATE, CANCEL, IGNORE }
}