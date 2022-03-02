// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemModificationType.EXTERNAL
import com.intellij.openapi.externalSystem.autoimport.changes.AsyncFilesChangesListener.Companion.subscribeOnDocumentsAndVirtualFilesChanges
import com.intellij.openapi.externalSystem.autoimport.changes.FilesChangesListener
import com.intellij.openapi.externalSystem.autoimport.changes.NewFilesListener.Companion.whenNewFilesCreated
import com.intellij.openapi.externalSystem.autoimport.settings.*
import com.intellij.openapi.externalSystem.util.calculateCrc
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.observable.operations.AnonymousParallelOperationTrace
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.LocalTimeCounter.currentTime
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

@ApiStatus.Internal
class ProjectSettingsTracker(
  private val project: Project,
  private val projectTracker: AutoImportProjectTracker,
  private val backgroundExecutor: Executor,
  private val projectAware: ExternalSystemProjectAware,
  private val parentDisposable: Disposable
) {

  private val status = ProjectStatus(debugName = "Settings ${projectAware.projectId.debugName}")

  private val settingsFilesStatus = AtomicReference(SettingsFilesStatus())

  private val applyChangesOperation = AnonymousParallelOperationTrace(debugName = "Apply changes operation")

  private val settingsAsyncSupplier = SettingsFilesAsyncSupplier()

  private fun calculateSettingsFilesCRC(settingsFiles: Set<String>): Map<String, Long> {
    val localFileSystem = LocalFileSystem.getInstance()
    return settingsFiles
      .mapNotNull { localFileSystem.findFileByPath(it) }
      .associate { it.path to calculateCrc(it) }
  }

  private fun calculateCrc(file: VirtualFile): Long {
    val fileDocumentManager = FileDocumentManager.getInstance()
    val document = fileDocumentManager.getCachedDocument(file)
    if (document != null) return document.calculateCrc(project, projectAware.projectId.systemId, file)
    return file.calculateCrc(project, projectAware.projectId.systemId)
  }

  fun isUpToDate() = status.isUpToDate()

  fun getModificationType() = status.getModificationType()

  fun getSettingsContext(): ExternalSystemSettingsFilesReloadContext {
    val modificationType = getModificationType()
    val status = settingsFilesStatus.get()
    return SettingsFilesReloadContext(modificationType, status.updated, status.created, status.deleted)
  }

  private fun createSettingsFilesStatus(
    oldSettingsFilesCRC: Map<String, Long>,
    newSettingsFilesCRC: Map<String, Long>
  ): SettingsFilesStatus {
    val updatedFiles = oldSettingsFilesCRC.keys.intersect(newSettingsFilesCRC.keys)
      .filterTo(HashSet()) { oldSettingsFilesCRC[it] != newSettingsFilesCRC[it] }
    val createdFiles = newSettingsFilesCRC.keys.minus(oldSettingsFilesCRC.keys)
    val deletedFiles = oldSettingsFilesCRC.keys.minus(newSettingsFilesCRC.keys)
    return SettingsFilesStatus(oldSettingsFilesCRC, newSettingsFilesCRC, updatedFiles, createdFiles, deletedFiles)
  }

  fun refreshChanges() {
    val operationStamp = currentTime()
    submitSettingsFilesRefresh { settingsPaths ->
      submitSettingsFilesCRCCalculation(settingsPaths) { newSettingsFilesCRC ->
        val settingsFilesStatus = settingsFilesStatus.updateAndGet {
          createSettingsFilesStatus(it.oldCRC, newSettingsFilesCRC)
        }
        LOG.info("[refreshChanges] Settings file status: ${settingsFilesStatus}")
        when (settingsFilesStatus.hasChanges()) {
          true -> status.markDirty(operationStamp, EXTERNAL)
          else -> status.markReverted(operationStamp)
        }
        projectTracker.scheduleChangeProcessing()
      }
    }
  }

  fun getState() = State(status.isDirty(), settingsFilesStatus.get().oldCRC.toMap())

  fun loadState(state: State) {
    if (state.isDirty) status.markDirty(currentTime(), EXTERNAL)
    settingsFilesStatus.set(SettingsFilesStatus(state.settingsFiles.toMap()))
  }

  private fun submitSettingsFilesCollection(invalidateCache: Boolean = false, callback: (Set<String>) -> Unit) {
    if (invalidateCache) {
      settingsAsyncSupplier.invalidate()
    }
    settingsAsyncSupplier.supply(callback, parentDisposable)
  }

  private fun submitSettingsFilesRefresh(callback: (Set<String>) -> Unit) {
    EdtAsyncSupplier.invokeOnEdt(projectTracker::isAsyncChangesProcessing, {
      val fileDocumentManager = FileDocumentManager.getInstance()
      fileDocumentManager.saveAllDocuments()
      submitSettingsFilesCollection(invalidateCache = true) { settingsPaths ->
        val localFileSystem = LocalFileSystem.getInstance()
        val settingsFiles = settingsPaths.map { Path.of(it) }
        localFileSystem.refreshNioFiles(settingsFiles, projectTracker.isAsyncChangesProcessing, false) {
          callback(settingsPaths)
        }
      }
    }, parentDisposable)
  }

  private fun submitSettingsFilesCRCCalculation(settingsPaths: Set<String>, vararg equality: Any, callback: (Map<String, Long>) -> Unit) {
    ReadAsyncSupplier.Builder { calculateSettingsFilesCRC(settingsPaths) }
      .shouldKeepTasksAsynchronous { projectTracker.isAsyncChangesProcessing }
      .coalesceBy(*equality)
      .build(backgroundExecutor)
      .supply(callback, parentDisposable)
  }

  fun beforeApplyChanges(listener: () -> Unit) = applyChangesOperation.beforeOperation(listener)
  fun afterApplyChanges(listener: () -> Unit) = applyChangesOperation.afterOperation(listener)

  init {
    projectAware.subscribe(ProjectListener(), parentDisposable)
    whenNewFilesCreated(settingsAsyncSupplier::invalidate, parentDisposable)
    subscribeOnDocumentsAndVirtualFilesChanges(settingsAsyncSupplier, ProjectSettingsListener(), parentDisposable)
  }

  data class State(var isDirty: Boolean = true, var settingsFiles: Map<String, Long> = emptyMap())

  private data class SettingsFilesStatus(
    val oldCRC: Map<String, Long> = emptyMap(),
    val newCRC: Map<String, Long> = emptyMap(),
    val updated: Set<String> = emptySet(),
    val created: Set<String> = emptySet(),
    val deleted: Set<String> = emptySet()
  ) {
    constructor(CRC: Map<String, Long>) : this(oldCRC = CRC)

    fun hasChanges() = updated.isNotEmpty() || created.isNotEmpty() || deleted.isNotEmpty()
  }

  private data class SettingsFilesReloadContext(
    override val modificationType: ExternalSystemModificationType,
    override val updated: Set<String>,
    override val created: Set<String>,
    override val deleted: Set<String>
  ) : ExternalSystemSettingsFilesReloadContext

  private inner class ProjectListener : ExternalSystemProjectListener {
    private var settingsFilesCRC: Map<String, Long> = emptyMap()

    override fun onProjectReloadStart() {
      applyChangesOperation.startTask()
      settingsFilesCRC = settingsFilesStatus.get().newCRC
    }

    override fun onProjectReloadFinish(status: ExternalSystemRefreshStatus) {
      val operationStamp = currentTime()
      submitSettingsFilesRefresh { settingsPaths ->
        submitSettingsFilesCRCCalculation(settingsPaths) { newSettingsFilesCRC ->
          val settingsFilesCRC = settingsFilesCRC
          val settingsFilesStatus = settingsFilesStatus.updateAndGet {
            createSettingsFilesStatus(newSettingsFilesCRC + settingsFilesCRC, newSettingsFilesCRC)
          }
          if (settingsFilesStatus.hasChanges()) {
            this@ProjectSettingsTracker.status.markDirty(operationStamp, EXTERNAL)
          }
          else {
            this@ProjectSettingsTracker.status.markSynchronized(operationStamp)
          }
          applyChangesOperation.finishTask()
        }
      }
    }

    override fun onSettingsFilesListChange() {
      val operationStamp = currentTime()
      submitSettingsFilesCollection (invalidateCache = true) { settingsPaths ->
        submitSettingsFilesCRCCalculation(settingsPaths) { newSettingsFilesCRC ->
          val settingsFilesStatus = settingsFilesStatus.updateAndGet {
            createSettingsFilesStatus(it.oldCRC, newSettingsFilesCRC)
          }
          LOG.info("[onSettingsFilesListChange] Settings file status: ${settingsFilesStatus}")
          when (settingsFilesStatus.hasChanges()) {
            true -> status.markModified(operationStamp, EXTERNAL)
            else -> status.markReverted(operationStamp)
          }
          projectTracker.scheduleChangeProcessing()
        }
      }
    }
  }

  private inner class ProjectSettingsListener : FilesChangesListener {
    override fun onFileChange(path: String, modificationStamp: Long, modificationType: ExternalSystemModificationType) {
      val operationStamp = currentTime()
      logModificationAsDebug(path, modificationStamp, modificationType)
      status.markModified(operationStamp, modificationType)
    }

    override fun apply() {
      val operationStamp = currentTime()
      submitSettingsFilesCollection { settingsPaths ->
        submitSettingsFilesCRCCalculation(settingsPaths, this, "apply") { newSettingsFilesCRC ->
          val settingsFilesStatus = settingsFilesStatus.updateAndGet {
            createSettingsFilesStatus(it.oldCRC, newSettingsFilesCRC)
          }
          if (!settingsFilesStatus.hasChanges()) {
            status.markReverted(operationStamp)
          }
          projectTracker.scheduleChangeProcessing()
        }
      }
    }

    private fun logModificationAsDebug(path: String, modificationStamp: Long, type: ExternalSystemModificationType) {
      if (LOG.isDebugEnabled) {
        val projectPath = projectAware.projectId.externalProjectPath
        val relativePath = FileUtil.getRelativePath(projectPath, path, '/') ?: path
        LOG.debug("File $relativePath is modified at ${modificationStamp} as $type")
      }
    }
  }

  private inner class SettingsFilesAsyncSupplier : AsyncSupplier<Set<String>> {
    private val cachingAsyncSupplier = CachingAsyncSupplier(
      BackgroundAsyncSupplier.Builder(projectAware::settingsFiles)
        .shouldKeepTasksAsynchronous(projectTracker::isAsyncChangesProcessing)
        .build(backgroundExecutor))
    private val supplier = BackgroundAsyncSupplier.Builder(cachingAsyncSupplier)
      .shouldKeepTasksAsynchronous(projectTracker::isAsyncChangesProcessing)
      .build(backgroundExecutor)

    override fun supply(consumer: (Set<String>) -> Unit, parentDisposable: Disposable) {
      supplier.supply({ consumer(it + settingsFilesStatus.get().oldCRC.keys) }, parentDisposable)
    }

    fun invalidate() = cachingAsyncSupplier.invalidate()
  }

  companion object {
    private val LOG = Logger.getInstance("#com.intellij.openapi.externalSystem.autoimport")
  }
}