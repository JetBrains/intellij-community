// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess.VfsRootAccessNotAllowedError
import com.intellij.util.indexing.PersistentDirtyFilesQueue.getQueueFile
import com.intellij.util.indexing.PersistentDirtyFilesQueue.removeCurrentFile
import com.intellij.util.indexing.UnindexedFilesScanner.LOG
import com.intellij.util.indexing.dependencies.AppIndexingDependenciesService
import com.intellij.util.indexing.dependencies.ProjectIndexingDependenciesService
import com.intellij.util.indexing.diagnostic.ScanningType
import com.intellij.util.indexing.events.FileIndexingRequest.Companion.updateRequest
import com.intellij.util.indexing.projectFilter.ProjectIndexableFilesFilterHolder
import com.intellij.util.indexing.projectFilter.usePersistentFilesFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture

@JvmField
internal val FIRST_SCANNING_REQUESTED: Key<FirstScanningState> = Key.create("FIRST_SCANNING_REQUESTED")
private val dumbModeThreshold: Int
  get() = Registry.intValue("scanning.dumb.mode.threshold", 20)

internal enum class FirstScanningState {
  REQUESTED, PERFORMED
}

internal fun scanAndIndexProjectAfterOpen(project: Project,
                                          startSuspended: Boolean,
                                          coroutineScope: CoroutineScope,
                                          indexingReason: String?) {
  FileBasedIndex.getInstance().loadIndexes()
  (project as UserDataHolderEx).putUserDataIfAbsent(FIRST_SCANNING_REQUESTED, FirstScanningState.REQUESTED)

  val dependenciesService = project.getService(ProjectIndexingDependenciesService::class.java)
  val filterHolder = (FileBasedIndex.getInstance() as FileBasedIndexImpl).indexableFilesFilterHolder
  val isFilterUpToDate = isIndexableFilesFilterUpToDate(project, filterHolder, dependenciesService)

  if (Registry.`is`("full.scanning.on.startup.can.be.skipped") && isFilterUpToDate) {
    scheduleDirtyFilesScanning(project, startSuspended, coroutineScope, indexingReason)
  }
  else {
    scheduleFullScanning(project, startSuspended, isFilterUpToDate, coroutineScope, indexingReason)
  }
}

private fun scheduleFullScanning(project: Project,
                                 startSuspended: Boolean,
                                 isFilterUpToDate: Boolean,
                                 coroutineScope: CoroutineScope,
                                 indexingReason: String?) {
  val someDirtyFilesScheduledForIndexingFuture = coroutineScope.async(Dispatchers.IO) {
    clearIndexesForDirtyFiles(project, false)
  }.asCompletableFuture()

  UnindexedFilesScanner(project, startSuspended, true, isFilterUpToDate, null, null, indexingReason,
                        ScanningType.FULL_ON_PROJECT_OPEN, someDirtyFilesScheduledForIndexingFuture)
    .queue()
}

private fun isShutdownPerformedForFileBasedIndex(fileBasedIndex: FileBasedIndexImpl) =
  fileBasedIndex.registeredIndexes?.isShutdownPerformed ?: true

private fun scheduleDirtyFilesScanning(project: Project,
                                       startSuspended: Boolean,
                                       coroutineScope: CoroutineScope,
                                       indexingReason: String?) {
  LOG.info("Skipping full scanning on startup because indexable files filter is up-to-date and 'full.scanning.on.startup.can.be.skipped' is set to true")
  val projectDirtyFilesFuture = coroutineScope.async(Dispatchers.IO) {
    clearIndexesForDirtyFiles(project, true)
  }
  UnindexedFilesScanner(project, startSuspended, true, true, listOf(DirtyFilesIndexableFilesIterator(projectDirtyFilesFuture)), null,
                        indexingReason, ScanningType.PARTIAL, projectDirtyFilesFuture.asCompletableFuture())
    .queue()
}

private suspend fun clearIndexesForDirtyFiles(project: Project, findAllVirtualFiles: Boolean): List<VirtualFile> {
  val fileBasedIndex = FileBasedIndex.getInstance() as FileBasedIndexImpl
  return if (isShutdownPerformedForFileBasedIndex(fileBasedIndex)) emptyList()
  else {
    val projectQueueFile = project.getQueueFile()
    val projectDirtyFileIds = PersistentDirtyFilesQueue.readIndexingQueue(projectQueueFile, ManagingFS.getInstance().creationTimestamp)
    val processedIds = fileBasedIndex.ensureDirtyFileIndexesDeleted()
    val projectProcessedIds = fileBasedIndex.ensureDirtyFileIndexesDeleted(projectDirtyFileIds)
    removeCurrentFile(projectQueueFile)
    val projectFiles = findProjectFiles(project, processedIds + projectProcessedIds, if (findAllVirtualFiles) -1 else dumbModeThreshold - 1)
    scheduleForIndexing(projectFiles, fileBasedIndex, dumbModeThreshold - 1)
    projectFiles
  }
}

private suspend fun findProjectFiles(project: Project, dirtyFilesIds: Collection<Int>, limit: Int = -1): List<VirtualFile> {
  return readAction {
    val fs = ManagingFS.getInstance()
    val fileBasedIndex = FileBasedIndex.getInstance() as FileBasedIndexImpl
    var exceptionLogged = false
    dirtyFilesIds.asSequence()
      .mapNotNull { fileId ->
        try {
          val file = fs.findFileById(fileId)
          if (file != null && fileBasedIndex.getContainingProjects(file).contains(project)) file else null
        }
        catch (e: VfsRootAccessNotAllowedError) {
          if (!exceptionLogged) {
            LOG.debug("VfsRootAccessNotAllowedError occurred. " +
                      "Probably previous test with different rules for project roots saved these files to dirty files queue. " +
                      "Example of error:", e)
            exceptionLogged = true
          }
          null
        }
      }.run {
        if (limit <= 0) this
        else this.take(limit)
      }.toList()
  }
}

private suspend fun scheduleForIndexing(someProjectDirtyFilesFiles: List<VirtualFile>, fileBasedIndex: FileBasedIndexImpl, limit: Int) {
  readActionBlocking {
    for (file in someProjectDirtyFilesFiles.run { if (limit > 0) take(limit) else this }) {
      fileBasedIndex.filesToUpdateCollector.scheduleForUpdate(updateRequest(file), emptyList())
    }
  }
}

private fun isIndexableFilesFilterUpToDate(project: Project,
                                           filterHolder: ProjectIndexableFilesFilterHolder,
                                           service: ProjectIndexingDependenciesService): Boolean {
  return service.isScanningCompleted() &&
         usePersistentFilesFilter() && filterHolder.wasDataLoadedFromDisk(project) &&
         project.getUserData(PERSISTENT_INDEXABLE_FILES_FILTER_INVALIDATED) != true &&
         ApplicationManager.getApplication().getService(AppIndexingDependenciesService::class.java).getCurrent().toInt() == service.getAppIndexingRequestIdOfLastScanning()
}

val PERSISTENT_INDEXABLE_FILES_FILTER_INVALIDATED = Key<Boolean>("PERSISTENT_INDEXABLE_FILES_FILTER_INVALIDATED")

fun invalidatePersistentIndexableFilesFilter(project: Project) {
  project.putUserData(PERSISTENT_INDEXABLE_FILES_FILTER_INVALIDATED, true)
}
