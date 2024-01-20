// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker.Companion.isAsyncChangesProcessing
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemModificationType.EXTERNAL
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemSettingsFilesModificationContext.Event.*
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemSettingsFilesModificationContext.ReloadStatus
import com.intellij.openapi.externalSystem.autoimport.ProjectStatus.ProjectEvent.*
import com.intellij.openapi.externalSystem.autoimport.ProjectStatus.ProjectEvent.Companion.externalInvalidate
import com.intellij.openapi.externalSystem.autoimport.ProjectStatus.ProjectEvent.Companion.externalModify
import com.intellij.openapi.externalSystem.autoimport.changes.AsyncFilesChangesListener.Companion.subscribeOnDocumentsAndVirtualFilesChanges
import com.intellij.openapi.externalSystem.autoimport.changes.FilesChangesListener
import com.intellij.openapi.externalSystem.autoimport.changes.NewFilesListener.Companion.whenNewFilesCreated
import com.intellij.openapi.externalSystem.autoimport.settings.*
import com.intellij.openapi.externalSystem.util.ExternalSystemActivityKey
import com.intellij.openapi.externalSystem.util.calculateCrc
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.observable.operation.core.AtomicOperationTrace
import com.intellij.openapi.observable.operation.core.isOperationInProgress
import com.intellij.openapi.observable.operation.core.whenOperationFinished
import com.intellij.openapi.observable.operation.core.whenOperationStarted
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.observation.trackActivityBlocking
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

  private val projectStatus = ProjectStatus(debugName = "Settings ${projectAware.projectId.debugName}")

  private val settingsFilesStatus = AtomicReference(SettingsFilesStatus())

  private val applyChangesOperation = AtomicOperationTrace(name = "Apply changes operation")

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

  fun isUpToDate() = projectStatus.isUpToDate()

  fun getModificationType() = projectStatus.getModificationType()

  fun getSettingsContext(): ExternalSystemSettingsFilesReloadContext {
    val status = settingsFilesStatus.get()
    return SettingsFilesReloadContext(status.updated, status.created, status.deleted)
  }

  /**
   * Updates settings files status using new CRCs.
   * @param isReloadJustFinished see [adjustCrc] for details
   */
  private fun updateSettingsFilesStatus(
    operationName: String,
    newCRC: Map<String, Long>,
    isReloadJustFinished: Boolean = false,
  ): SettingsFilesStatus {
    return settingsFilesStatus.updateAndGet {
      SettingsFilesStatus(it.oldCRC, newCRC)
        .adjustCrc(operationName, isReloadJustFinished)
    }
  }

  /**
   * Adjusts settings files status. It allows ignoring files modifications by rules from
   * external system. For example some build systems needed to ignore files updates during reload.
   *
   * @see ExternalSystemProjectAware.isIgnoredSettingsFileEvent
   */
  private fun SettingsFilesStatus.adjustCrc(operationName: String, isReloadJustFinished: Boolean): SettingsFilesStatus {
    val modificationType = getModificationType()
    val isReloadInProgress = applyChangesOperation.isOperationInProgress()
    val reloadStatus = when {
      isReloadJustFinished -> ReloadStatus.JUST_FINISHED
      isReloadInProgress -> ReloadStatus.IN_PROGRESS
      else -> ReloadStatus.IDLE
    }
    val oldCRC = oldCRC.toMutableMap()
    for (path in updated) {
      val context = SettingsFilesModificationContext(UPDATE, modificationType, reloadStatus)
      if (projectAware.isIgnoredSettingsFileEvent(path, context)) {
        oldCRC[path] = newCRC[path]!!
      }
    }
    for (path in created) {
      val context = SettingsFilesModificationContext(CREATE, modificationType, reloadStatus)
      if (projectAware.isIgnoredSettingsFileEvent(path, context)) {
        oldCRC[path] = newCRC[path]!!
      }
    }
    for (path in deleted) {
      val context = SettingsFilesModificationContext(DELETE, modificationType, reloadStatus)
      if (projectAware.isIgnoredSettingsFileEvent(path, context)) {
        oldCRC.remove(path)
      }
    }
    val status = SettingsFilesStatus(oldCRC, newCRC)
    LOG.debug(
      "[$operationName] " +
      "ReloadStatus=$reloadStatus, " +
      "ModificationType=$modificationType, " +
      "Updated=${status.updated}, " +
      "Created=${status.created}, " +
      "Deleted=${status.deleted}"
    )
    return status
  }

  fun getState() = State(projectStatus.isDirty(), settingsFilesStatus.get().oldCRC.toMap())

  fun loadState(state: State) {
    val operationStamp = currentTime()
    settingsFilesStatus.set(SettingsFilesStatus(state.settingsFiles.toMap()))
    when (state.isDirty) {
      true -> projectStatus.markDirty(operationStamp, EXTERNAL)
      else -> projectStatus.markSynchronized(operationStamp)
    }
  }

  fun refreshChanges() {
    submitSettingsFilesStatusUpdate(
      operationName = "refreshChanges",
      isRefreshVfs = true,
      syncEvent = ::Revert,
      changeEvent = ::externalInvalidate
    ) {
      projectTracker.scheduleChangeProcessing()
    }
  }

  private fun submitSettingsFilesCollection(isInvalidateCache: Boolean = false, callback: (Set<String>) -> Unit) {
    if (isInvalidateCache) {
      settingsAsyncSupplier.invalidate()
    }
    settingsAsyncSupplier.supply(parentDisposable, callback)
  }

  private fun submitSettingsFilesRefresh(callback: (Set<String>) -> Unit) {
    EdtAsyncSupplier.invokeOnEdt(::isAsyncChangesProcessing, parentDisposable) {
      val fileDocumentManager = FileDocumentManager.getInstance()
      fileDocumentManager.saveAllDocuments()
      submitSettingsFilesCollection(isInvalidateCache = true) { settingsPaths ->
        val localFileSystem = LocalFileSystem.getInstance()
        val settingsFiles = settingsPaths.map { Path.of(it) }
        localFileSystem.refreshNioFiles(settingsFiles, isAsyncChangesProcessing, false) {
          callback(settingsPaths)
        }
      }
    }
  }

  private fun submitSettingsFilesCRCCalculation(
    operationName: String,
    settingsPaths: Set<String>,
    isMergeSameCalls: Boolean = false,
    callback: (Map<String, Long>) -> Unit
  ) {
    val builder = ReadAsyncSupplier.Builder { calculateSettingsFilesCRC(settingsPaths) }
      .shouldKeepTasksAsynchronous(::isAsyncChangesProcessing)
    if (isMergeSameCalls) {
      builder.coalesceBy(this, operationName)
    }
    builder.build(backgroundExecutor)
      .supply(parentDisposable, callback)
  }

  private fun submitSettingsFilesCollection(
    isRefreshVfs: Boolean = false,
    isInvalidateCache: Boolean = false,
    callback: (Set<String>) -> Unit
  ) {
    if (isRefreshVfs) {
      submitSettingsFilesRefresh(callback)
    }
    else {
      submitSettingsFilesCollection(isInvalidateCache, callback)
    }
  }

  private fun submitSettingsFilesStatusUpdate(
    operationName: String,
    isMergeSameCalls: Boolean = false,
    isReloadJustFinished: Boolean = false,
    isInvalidateCache: Boolean = false,
    isRefreshVfs: Boolean = false,
    syncEvent: (Long) -> ProjectStatus.ProjectEvent,
    changeEvent: ((Long) -> ProjectStatus.ProjectEvent)?,
    callback: () -> Unit
  ) {
    submitSettingsFilesCollection(isRefreshVfs, isInvalidateCache) { settingsPaths ->
      val operationStamp = currentTime()
      submitSettingsFilesCRCCalculation(operationName, settingsPaths, isMergeSameCalls) { newSettingsFilesCRC ->
        val settingsFilesStatus = updateSettingsFilesStatus(operationName, newSettingsFilesCRC, isReloadJustFinished)
        val event = if (settingsFilesStatus.hasChanges()) changeEvent else syncEvent
        if (event != null) {
          projectStatus.update(event(operationStamp))
        }
        callback()
      }
    }
  }

  fun beforeApplyChanges(
    parentDisposable: Disposable,
    listener: () -> Unit
  ) = applyChangesOperation.whenOperationStarted(parentDisposable, listener)

  fun afterApplyChanges(
    parentDisposable: Disposable,
    listener: () -> Unit
  ) = applyChangesOperation.whenOperationFinished(parentDisposable, listener)

  init {
    projectAware.subscribe(ProjectListener(), parentDisposable)
    whenNewFilesCreated(settingsAsyncSupplier::invalidate, parentDisposable)
    subscribeOnDocumentsAndVirtualFilesChanges(settingsAsyncSupplier, ProjectSettingsListener(), parentDisposable)
  }

  data class State(var isDirty: Boolean = true, var settingsFiles: Map<String, Long> = emptyMap())

  private class SettingsFilesStatus(
    val oldCRC: Map<String, Long>,
    val newCRC: Map<String, Long>,
  ) {

    constructor(CRC: Map<String, Long> = emptyMap()) : this(CRC, CRC)

    val updated: Set<String> by lazy {
      oldCRC.keys.intersect(newCRC.keys)
        .filterTo(HashSet()) { oldCRC[it] != newCRC[it] }
    }

    val created: Set<String> by lazy {
      newCRC.keys.minus(oldCRC.keys)
    }

    val deleted: Set<String> by lazy {
      oldCRC.keys.minus(newCRC.keys)
    }

    fun hasChanges() = updated.isNotEmpty() || created.isNotEmpty() || deleted.isNotEmpty()
  }

  private class SettingsFilesReloadContext(
    override val updated: Set<String>,
    override val created: Set<String>,
    override val deleted: Set<String>
  ) : ExternalSystemSettingsFilesReloadContext

  private class SettingsFilesModificationContext(
    override val event: ExternalSystemSettingsFilesModificationContext.Event,
    override val modificationType: ExternalSystemModificationType,
    override val reloadStatus: ReloadStatus,
  ) : ExternalSystemSettingsFilesModificationContext

  private inner class ProjectListener : ExternalSystemProjectListener {

    override fun onProjectReloadStart() {
      applyChangesOperation.traceStart()
      settingsFilesStatus.updateAndGet {
        SettingsFilesStatus(it.newCRC, it.newCRC)
      }
      projectStatus.markSynchronized(currentTime())
    }

    override fun onProjectReloadFinish(status: ExternalSystemRefreshStatus) {
      submitSettingsFilesStatusUpdate(
        operationName = "onProjectReloadFinish",
        isRefreshVfs = true,
        isReloadJustFinished = true,
        syncEvent = ::Synchronize,
        changeEvent = ::externalInvalidate
      ) {
        applyChangesOperation.traceFinish()
      }
    }

    override fun onSettingsFilesListChange() {
      submitSettingsFilesStatusUpdate(
        operationName = "onSettingsFilesListChange",
        isInvalidateCache = true,
        syncEvent = ::Revert,
        changeEvent = ::externalModify,
      ) {
        projectTracker.scheduleChangeProcessing()
      }
    }
  }

  private inner class ProjectSettingsListener : FilesChangesListener {

    override fun onFileChange(path: String, modificationStamp: Long, modificationType: ExternalSystemModificationType) {
      val operationStamp = currentTime()
      val adjustedModificationType = projectAware.adjustModificationType(path, modificationType)
      logModificationAsDebug(path, modificationStamp, modificationType, adjustedModificationType)
      projectStatus.markModified(operationStamp, adjustedModificationType)
    }

    override fun apply() {
      submitSettingsFilesStatusUpdate(
        operationName = "ProjectSettingsListener.apply",
        isMergeSameCalls = true,
        syncEvent = ::Revert,
        changeEvent = null
      ) {
        projectTracker.scheduleChangeProcessing()
      }
    }

    private fun logModificationAsDebug(path: String,
                                       modificationStamp: Long,
                                       type: ExternalSystemModificationType,
                                       adjustedType: ExternalSystemModificationType) {
      if (LOG.isDebugEnabled) {
        val projectPath = projectAware.projectId.externalProjectPath
        val relativePath = FileUtil.getRelativePath(projectPath, path, '/') ?: path
        LOG.debug("File $relativePath is modified at ${modificationStamp} as $type (adjusted to $adjustedType)")
      }
    }
  }

  private inner class SettingsFilesAsyncSupplier : AsyncSupplier<Set<String>> {

    private val cachingAsyncSupplier = CachingAsyncSupplier(
      BackgroundAsyncSupplier.Builder(projectAware::settingsFiles)
        .shouldKeepTasksAsynchronous(::isAsyncChangesProcessing)
        .build(backgroundExecutor))

    private val supplier = BackgroundAsyncSupplier.Builder(cachingAsyncSupplier)
      .shouldKeepTasksAsynchronous(::isAsyncChangesProcessing)
      .build(backgroundExecutor)

    override fun supply(parentDisposable: Disposable, consumer: (Set<String>) -> Unit) {
      project.trackActivityBlocking(ExternalSystemActivityKey) {
        supplier.supply(parentDisposable) {
          consumer(it + settingsFilesStatus.get().oldCRC.keys)
        }
      }
    }

    fun invalidate() {
      cachingAsyncSupplier.invalidate()
    }
  }

  companion object {
    private val LOG = Logger.getInstance("#com.intellij.openapi.externalSystem.autoimport")
  }
}