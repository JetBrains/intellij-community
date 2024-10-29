// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemRefreshStatus.SUCCESS
import com.intellij.openapi.externalSystem.autoimport.MockProjectAware.ReloadCollisionPassType.*
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.observable.dispatcher.SingleEventDispatcher
import com.intellij.openapi.observable.operation.core.AtomicOperationTrace
import com.intellij.openapi.observable.operation.core.isOperationInProgress
import com.intellij.openapi.observable.operation.core.traceRun
import com.intellij.openapi.observable.operation.core.withCompletedOperation
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.observation.ActivityKey
import com.intellij.platform.backend.observation.Observation
import com.intellij.platform.backend.observation.trackActivityBlocking
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

class MockProjectAware(
  override val projectId: ExternalSystemProjectId,
  private val project: Project,
  private val parentDisposable: Disposable,
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

  val modificationTypeAdjustingRule: AtomicReference<(String, ExternalSystemModificationType) -> ExternalSystemModificationType> =
    AtomicReference { _, type -> type }

  fun resetAssertionCounters() {
    settingsAccessCounter.set(0)
    reloadCounter.set(0)
    subscribeCounter.set(0)
    unsubscribeCounter.set(0)
  }

  fun registerSettingsFile(file: VirtualFile) {
    registerSettingsFile(file.toNioPath())
  }

  fun registerSettingsFile(path: Path) {
    _settingsFiles.add(path.toCanonicalPath())
  }

  fun ignoreSettingsFileWhen(file: VirtualFile, condition: (ExternalSystemSettingsFilesModificationContext) -> Boolean) {
    ignoreSettingsFileWhen(file.toNioPath(), condition)
  }

  fun ignoreSettingsFileWhen(path: Path, condition: (ExternalSystemSettingsFilesModificationContext) -> Boolean) {
    ignoredSettingsFiles[path.toCanonicalPath()] = condition
  }

  override fun isIgnoredSettingsFileEvent(path: String, context: ExternalSystemSettingsFilesModificationContext): Boolean {
    if (super.isIgnoredSettingsFileEvent(path, context)) {
      return true
    }
    val condition = ignoredSettingsFiles[path] ?: return false
    return condition(context)
  }

  override fun adjustModificationType(path: String, modificationType: ExternalSystemModificationType): ExternalSystemModificationType {
    val rule = modificationTypeAdjustingRule.get()
    return rule(path, modificationType)
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
      invokeAndWaitIfNeeded {
        val fileDocumentManager = FileDocumentManager.getInstance()
        fileDocumentManager.saveAllDocuments()
      }
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
      ApplicationManager.getApplication().executeOnPooledThread(action)
    }
    else {
      action()
    }
  }

  val LOG = Logger.getInstance(MockProjectAware::class.java)

  fun <R> waitForAllProjectActivities(action: () -> R): R {
    return project.trackActivityBlocking(MockProjectReloadActivityKey, action)
      .also {
        runBlocking {
          withTimeout(10.seconds) {
            Observation.awaitConfiguration(project) { message ->
              LOG.debug(message)
            }
          }
        }
      }
  }

  private object MockProjectReloadActivityKey : ActivityKey {
    override val presentableName: String = "mock project reload"
  }

  enum class ReloadCollisionPassType { DUPLICATE, CANCEL, IGNORE }
}