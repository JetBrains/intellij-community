// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker.Companion.isAsyncChangesProcessing
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemModificationType.EXTERNAL
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemSettingsFilesModificationContext.Event
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemSettingsFilesModificationContext.ReloadStatus
import com.intellij.openapi.externalSystem.autoimport.ProjectStatus.ProjectEvent.Companion.externalInvalidate
import com.intellij.openapi.externalSystem.autoimport.ProjectStatus.ProjectEvent.Companion.externalModify
import com.intellij.openapi.externalSystem.autoimport.ProjectStatus.ProjectEvent.Revert
import com.intellij.openapi.externalSystem.autoimport.ProjectStatus.ProjectEvent.Synchronize
import com.intellij.openapi.externalSystem.autoimport.ProjectStatus.Stamp
import com.intellij.openapi.externalSystem.autoimport.changes.AsyncFileChangesListener.Companion.subscribeOnDocumentsAndVirtualFilesChanges
import com.intellij.openapi.externalSystem.autoimport.changes.FilesChangesListener
import com.intellij.openapi.externalSystem.autoimport.changes.NewFilesListener.Companion.whenNewFilesCreated
import com.intellij.openapi.externalSystem.autoimport.settings.AsyncSupplier
import com.intellij.openapi.externalSystem.autoimport.settings.BackgroundAsyncSupplier
import com.intellij.openapi.externalSystem.autoimport.settings.tracked
import com.intellij.openapi.externalSystem.service.ui.completion.cache.AsyncLocalCache
import com.intellij.openapi.externalSystem.util.calculateCrc
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.observable.operation.core.AtomicOperationTrace
import com.intellij.openapi.observable.operation.core.isOperationInProgress
import com.intellij.openapi.observable.operation.core.whenOperationFinished
import com.intellij.openapi.observable.operation.core.whenOperationStarted
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.serialization.Serializable
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
      .mapNotNull {
        val crc = calculateCrc(it)
        if (crc != 0L) it.path to crc else null
      }
      .toMap()
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
   *
   * @param reloadStatus see [adjustCrc] for details
   */
  private fun updateSettingsFilesStatus(
    operationName: String,
    newCRC: Map<String, Long>,
    reloadStatus: ReloadStatus,
  ): SettingsFilesStatus {
    return settingsFilesStatus.updateAndGet {
      SettingsFilesStatus(it.oldCRC, newCRC)
        .adjustCrc(operationName, reloadStatus)
    }
  }

  /**
   * Adjusts settings files status.
   *
   * It allows ignoring files modifications by rules from an external system.
   * For example, some build systems needed to ignore file updates during reload.
   *
   * @see ExternalSystemProjectAware.isIgnoredSettingsFileEvent
   */
  private fun SettingsFilesStatus.adjustCrc(
    operationName: String,
    reloadStatus: ReloadStatus,
  ): SettingsFilesStatus {
    val modificationType = getModificationType()
    val oldCRC = oldCRC.toMutableMap()
    for (path in updated) {
      val context = SettingsFilesModificationContext(Event.UPDATE, modificationType, reloadStatus)
      if (projectAware.isIgnoredSettingsFileEvent(path, context)) {
        oldCRC[path] = newCRC[path]!!
      }
    }
    for (path in created) {
      val context = SettingsFilesModificationContext(Event.CREATE, modificationType, reloadStatus)
      if (projectAware.isIgnoredSettingsFileEvent(path, context)) {
        oldCRC[path] = newCRC[path]!!
      }
    }
    for (path in deleted) {
      val context = SettingsFilesModificationContext(Event.DELETE, modificationType, reloadStatus)
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
    val operationStamp = Stamp.nextStamp()
    settingsFilesStatus.set(SettingsFilesStatus(state.settingsFiles.toMap()))
    when (state.isDirty) {
      true -> projectStatus.markDirty(operationStamp, EXTERNAL)
      else -> projectStatus.markSynchronized(operationStamp)
    }
  }

  fun refreshChanges() {
    submitSettingsFilesStatusUpdate("refreshChanges") {
      isRefreshVfs = true
      syncEvent = ::Revert
      changeEvent = ::externalInvalidate
      callback = { projectTracker.scheduleChangeProcessing() }
    }
  }

  private fun submitSettingsFilesCollection(
    isRefreshVfs: Boolean,
    isInvalidateCache: Boolean,
    callback: (Set<String>) -> Unit,
  ) {
    if (isInvalidateCache || isRefreshVfs) {
      settingsAsyncSupplier.invalidate()
    }
    settingsAsyncSupplier.supply(parentDisposable) { settingsPaths ->
      if (isRefreshVfs) {
        val settingsFiles = settingsPaths.mapNotNull { Path.of(it) }
        if (settingsFiles.isNotEmpty()) {
          LocalFileSystem.getInstance().refreshNioFiles(settingsFiles)
        }
      }
      callback(settingsPaths)
    }
  }

  private fun submitSettingsFilesStatusUpdate(
    operationName: String,
    configureContext: SettingsFilesStatusUpdateContext.() -> Unit
  ) {
    val reloadStatus = when (applyChangesOperation.isOperationInProgress()) {
      true -> ReloadStatus.IN_PROGRESS
      else -> ReloadStatus.IDLE
    }
    val context = SettingsFilesStatusUpdateContext(reloadStatus)
    context.configureContext()
    submitSettingsFilesStatusUpdate(operationName, context)
  }

  private fun submitSettingsFilesStatusUpdate(
    operationName: String,
    context: SettingsFilesStatusUpdateContext,
  ) {
    submitSettingsFilesCollection(context.isRefreshVfs, context.isInvalidateCache) { settingsPaths ->
      // This operation is used for ordering between an update operation and file changes listener.
      // Therefore, the operation stamp cannot be taken earlier than VFS refresh for the settings files.
      // @see the AsyncFileChangesListener#apply function for details
      val operationStamp = Stamp.nextStamp()
      val newSettingsFilesCRC = runReadAction {
        calculateSettingsFilesCRC(settingsPaths)
      }
      val settingsFilesStatus = updateSettingsFilesStatus(operationName, newSettingsFilesCRC, context.reloadStatus)
      updateProjectStatus(operationStamp, context.syncEvent, context.changeEvent, settingsFilesStatus)
      context.callback?.invoke()
    }
  }

  private fun updateProjectStatus(
    operationStamp: Stamp,
    syncEvent: ((Stamp) -> ProjectStatus.ProjectEvent)?,
    changeEvent: ((Stamp) -> ProjectStatus.ProjectEvent)?,
    settingsFilesStatus: SettingsFilesStatus,
  ) {
    val event = when (settingsFilesStatus.hasChanges()) {
      true -> changeEvent
      else -> syncEvent
    }
    if (event != null) {
      projectStatus.update(event(operationStamp))
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

  @Serializable
  data class State(
    @JvmField val isDirty: Boolean = true,
    @JvmField val settingsFiles: Map<String, Long> = emptyMap(),
  )

  private class SettingsFilesStatusUpdateContext(
    var reloadStatus: ReloadStatus,
    var isRefreshVfs: Boolean = false,
    var isInvalidateCache: Boolean = false,
    var syncEvent: ((Stamp) -> ProjectStatus.ProjectEvent)? = null,
    var changeEvent: ((Stamp) -> ProjectStatus.ProjectEvent)? = null,
    var callback: (() -> Unit)? = null,
  )

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
    override val event: Event,
    override val modificationType: ExternalSystemModificationType,
    override val reloadStatus: ReloadStatus,
  ) : ExternalSystemSettingsFilesModificationContext

  private inner class ProjectListener : ExternalSystemProjectListener {

    override fun onProjectReloadStart() {
      applyChangesOperation.traceStart()
      submitSettingsFilesStatusUpdate("onProjectReloadStart") {
        isRefreshVfs = true
        reloadStatus = ReloadStatus.JUST_STARTED
        syncEvent = ::Synchronize
        changeEvent = ::externalInvalidate
      }
    }

    override fun onProjectReloadFinish(status: ExternalSystemRefreshStatus) {
      submitSettingsFilesStatusUpdate("onProjectReloadFinish") {
        isRefreshVfs = true
        reloadStatus = ReloadStatus.JUST_FINISHED
        syncEvent = ::Synchronize
        changeEvent = ::externalInvalidate
        callback = { applyChangesOperation.traceFinish() }
      }
    }

    override fun onSettingsFilesListChange() {
      submitSettingsFilesStatusUpdate("onSettingsFilesListChange") {
        isInvalidateCache = true
        syncEvent = ::Revert
        changeEvent = ::externalModify
        callback = { projectTracker.scheduleChangeProcessing() }
      }
    }
  }

  private inner class ProjectSettingsListener : FilesChangesListener {

    override fun onFileChange(stamp: Stamp, path: String, modificationStamp: Long, modificationType: ExternalSystemModificationType) {
      val adjustedModificationType = projectAware.adjustModificationType(path, modificationType)
      logModificationAsDebug(path, modificationStamp, modificationType, adjustedModificationType)
      projectStatus.markModified(stamp, adjustedModificationType)
    }

    override fun apply() {
      submitSettingsFilesStatusUpdate("ProjectSettingsListener.apply") {
        syncEvent = ::Revert
        callback = { projectTracker.scheduleChangeProcessing() }
      }
    }

    private fun logModificationAsDebug(path: String,
                                       modificationStamp: Long,
                                       type: ExternalSystemModificationType,
                                       adjustedType: ExternalSystemModificationType) {
      if (LOG.isDebugEnabled) {
        val projectPath = projectAware.projectId.externalProjectPath
        val relativePath = FileUtil.getRelativePath(projectPath, path, '/') ?: path
        LOG.debug("File $relativePath is modified at $modificationStamp as $type (adjusted to $adjustedType)")
      }
    }
  }

  private inner class SettingsFilesAsyncSupplier : AsyncSupplier<Set<String>> {

    private val modificationTracker = SimpleModificationTracker()

    private val settingsFilesCache = AsyncLocalCache<Set<String>>()

    private fun getOrCollectSettingsFiles(): Set<String> {
      return settingsFilesCache.getOrCreateValueBlocking(modificationTracker.modificationCount) {
        projectAware.settingsFiles
      }
    }

    private val supplier = BackgroundAsyncSupplier.Builder(::getOrCollectSettingsFiles)
      .shouldKeepTasksAsynchronous(::isAsyncChangesProcessing)
      .build(backgroundExecutor)
      .tracked(project)

    override fun supply(parentDisposable: Disposable, consumer: (Set<String>) -> Unit) {
      supplier.supply(parentDisposable) {
        consumer(it + settingsFilesStatus.get().oldCRC.keys)
      }
    }

    fun invalidate() {
      modificationTracker.incModificationCount()
    }
  }

  companion object {
    private val LOG = Logger.getInstance("#com.intellij.openapi.externalSystem.autoimport")
  }
}