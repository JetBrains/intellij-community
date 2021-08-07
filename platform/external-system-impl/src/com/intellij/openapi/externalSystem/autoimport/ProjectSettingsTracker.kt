// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.autoimport.ProjectStatus.ModificationType
import com.intellij.openapi.externalSystem.autoimport.ProjectStatus.ModificationType.EXTERNAL
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

  private val status = ProjectStatus(debugName = "Settings ${projectAware.projectId.readableName}")

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

  fun getSettingsContext(): ExternalSystemSettingsFilesReloadContext = settingsFilesStatus.get()

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

  /**
   * Usually all crc hashes must be previously calculated
   *  => this apply will be fast
   *  => collisions is a rare thing
   */
  private fun applyChanges() {
    applyChangesOperation.startTask()
    submitSettingsFilesRefreshAndCRCCalculation("applyChanges") { newSettingsFilesCRC ->
      settingsFilesStatus.set(SettingsFilesStatus(newSettingsFilesCRC))
      status.markSynchronized(currentTime())
      applyChangesOperation.finishTask()
    }
  }

  /**
   * Applies changes for newly registered files
   * Needed to cases: tracked files are registered during project reload
   */
  private fun applyUnknownChanges() {
    applyChangesOperation.startTask()
    submitSettingsFilesRefreshAndCRCCalculation("applyUnknownChanges") { newSettingsFilesCRC ->
      val settingsFilesStatus = settingsFilesStatus.updateAndGet {
        createSettingsFilesStatus(newSettingsFilesCRC + it.oldCRC, newSettingsFilesCRC)
      }
      if (!settingsFilesStatus.hasChanges()) {
        status.markSynchronized(currentTime())
      }
      applyChangesOperation.finishTask()
    }
  }

  fun refreshChanges() {
    submitSettingsFilesRefreshAndCRCCalculation("refreshChanges") { newSettingsFilesCRC ->
      val settingsFilesStatus = settingsFilesStatus.updateAndGet {
        createSettingsFilesStatus(it.oldCRC, newSettingsFilesCRC)
      }
      LOG.info("Settings file status: ${settingsFilesStatus}")
      when (settingsFilesStatus.hasChanges()) {
        true -> status.markDirty(currentTime(), EXTERNAL)
        else -> status.markReverted(currentTime())
      }
      projectTracker.scheduleChangeProcessing()
    }
  }

  fun getState() = State(status.isDirty(), settingsFilesStatus.get().oldCRC.toMap())

  fun loadState(state: State) {
    if (state.isDirty) status.markDirty(currentTime(), EXTERNAL)
    settingsFilesStatus.set(SettingsFilesStatus(state.settingsFiles.toMap()))
  }

  private fun submitSettingsFilesRefreshAndCRCCalculation(id: Any, callback: (Map<String, Long>) -> Unit) {
    submitSettingsFilesRefresh { settingsPaths ->
      submitSettingsFilesCRCCalculation(id, settingsPaths, callback)
    }
  }

  @Suppress("SameParameterValue")
  private fun submitSettingsFilesCRCCalculation(id: Any, callback: (Map<String, Long>) -> Unit) {
    settingsAsyncSupplier.supply({ settingsPaths ->
                                   submitSettingsFilesCRCCalculation(id, settingsPaths, callback)
                                 }, parentDisposable)
  }

  private fun submitSettingsFilesRefresh(callback: (Set<String>) -> Unit) {
    EdtAsyncSupplier.invokeOnEdt(projectTracker::isAsyncChangesProcessing, {
      val fileDocumentManager = FileDocumentManager.getInstance()
      fileDocumentManager.saveAllDocuments()
      settingsAsyncSupplier.invalidate()
      settingsAsyncSupplier.supply({ settingsPaths ->
        val localFileSystem = LocalFileSystem.getInstance()
        val settingsFiles = settingsPaths.map { Path.of(it) }
        localFileSystem.refreshNioFiles(settingsFiles, projectTracker.isAsyncChangesProcessing, false) {
          callback(settingsPaths)
        }
      }, parentDisposable)
    }, parentDisposable)
  }

  private fun submitSettingsFilesCRCCalculation(id: Any, settingsPaths: Set<String>, callback: (Map<String, Long>) -> Unit) {
    ReadAsyncSupplier.Builder { calculateSettingsFilesCRC(settingsPaths) }
      .shouldKeepTasksAsynchronous { projectTracker.isAsyncChangesProcessing }
      .coalesceBy(this, id)
      .build(backgroundExecutor)
      .supply(callback, parentDisposable)
  }

  fun beforeApplyChanges(listener: () -> Unit) = applyChangesOperation.beforeOperation(listener)
  fun afterApplyChanges(listener: () -> Unit) = applyChangesOperation.afterOperation(listener)

  init {
    val projectRefreshListener = object : ExternalSystemProjectRefreshListener {
      override fun beforeProjectRefresh() {
        applyChangesOperation.startTask()
        applyChanges()
      }

      override fun afterProjectRefresh(status: ExternalSystemRefreshStatus) {
        applyUnknownChanges()
        applyChangesOperation.finishTask()
      }
    }
    projectAware.subscribe(projectRefreshListener, parentDisposable)
  }

  init {
    whenNewFilesCreated(settingsAsyncSupplier::invalidate, parentDisposable)
    subscribeOnDocumentsAndVirtualFilesChanges(settingsAsyncSupplier, ProjectSettingsListener(), parentDisposable)
  }

  companion object {
    private val LOG = Logger.getInstance("#com.intellij.openapi.externalSystem.autoimport")
  }

  data class State(var isDirty: Boolean = true, var settingsFiles: Map<String, Long> = emptyMap())

  private data class SettingsFilesStatus(
    val oldCRC: Map<String, Long> = emptyMap(),
    val newCRC: Map<String, Long> = emptyMap(),
    override val updated: Set<String> = emptySet(),
    override val created: Set<String> = emptySet(),
    override val deleted: Set<String> = emptySet()
  ) : ExternalSystemSettingsFilesReloadContext {
    constructor(CRC: Map<String, Long>) : this(oldCRC = CRC)

    fun hasChanges() = updated.isNotEmpty() || created.isNotEmpty() || deleted.isNotEmpty()
  }

  private inner class ProjectSettingsListener : FilesChangesListener {
    override fun onFileChange(path: String, modificationStamp: Long, modificationType: ModificationType) {
      logModificationAsDebug(path, modificationStamp, modificationType)
      if (applyChangesOperation.isOperationCompleted()) {
        status.markModified(currentTime(), modificationType)
      }
      else {
        status.markDirty(currentTime(), modificationType)
      }
    }

    override fun apply() {
      submitSettingsFilesCRCCalculation("apply") { newSettingsFilesCRC ->
        val settingsFilesStatus = settingsFilesStatus.updateAndGet {
          createSettingsFilesStatus(it.oldCRC, newSettingsFilesCRC)
        }
        if (!settingsFilesStatus.hasChanges()) {
          status.markReverted(currentTime())
        }
        projectTracker.scheduleChangeProcessing()
      }
    }

    private fun logModificationAsDebug(path: String, modificationStamp: Long, type: ModificationType) {
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
}