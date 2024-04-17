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
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess.VfsRootAccessNotAllowedError
import com.intellij.util.indexing.PersistentDirtyFilesQueue.getQueueFile
import com.intellij.util.indexing.PersistentDirtyFilesQueue.removeCurrentFile
import com.intellij.util.indexing.UnindexedFilesScanner.Companion.LOG
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
import kotlin.math.max

@JvmField
internal val FIRST_SCANNING_REQUESTED: Key<FirstScanningState> = Key.create("FIRST_SCANNING_REQUESTED")
private val dumbModeThreshold: Int
  get() = Registry.intValue("scanning.dumb.mode.threshold", 20)

internal enum class FirstScanningState {
  REQUESTED, PERFORMED
}

internal fun scanAndIndexProjectAfterOpen(project: Project,
                                          startSuspended: Boolean,
                                          allowSkippingScanning: Boolean,
                                          coroutineScope: CoroutineScope,
                                          indexingReason: String) {
  FileBasedIndex.getInstance().loadIndexes()
  (project as UserDataHolderEx).putUserDataIfAbsent(FIRST_SCANNING_REQUESTED, FirstScanningState.REQUESTED)

  val filterHolder = (FileBasedIndex.getInstance() as FileBasedIndexImpl).indexableFilesFilterHolder
  val isFilterUpToDate = isIndexableFilesFilterUpToDate(project, filterHolder)

  if (allowSkippingScanning && Registry.`is`("full.scanning.on.startup.can.be.skipped") && isFilterUpToDate) {
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
                                 indexingReason: String) {
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
                                       indexingReason: String) {
  LOG.info("Skipping full scanning on startup because indexable files filter is up-to-date and 'full.scanning.on.startup.can.be.skipped' is set to true")
  val projectDirtyFiles = coroutineScope.async(Dispatchers.IO) {
    clearIndexesForDirtyFiles(project, true)
  }
  val projectDirtyFilesFromProjectQueue = coroutineScope.async { projectDirtyFiles.await()?.projectDirtyFilesFromProjectQueue ?: emptyList() }
  val projectDirtyFilesFromOrphanQueue = coroutineScope.async { projectDirtyFiles.await()?.projectDirtyFilesFromOrphanQueue ?: emptyList() }
  val iterators = listOf(DirtyFilesIndexableFilesIterator(projectDirtyFilesFromProjectQueue, false),
                         DirtyFilesIndexableFilesIterator(projectDirtyFilesFromOrphanQueue, true))
  UnindexedFilesScanner(project, startSuspended, true, true, iterators, null,
                        indexingReason, ScanningType.PARTIAL, projectDirtyFiles.asCompletableFuture())
    .queue()
}

private suspend fun clearIndexesForDirtyFiles(project: Project, findAllVirtualFiles: Boolean): ResultOfClearIndexesForDirtyFiles? {
  val fileBasedIndex = FileBasedIndex.getInstance() as FileBasedIndexImpl
  return if (isShutdownPerformedForFileBasedIndex(fileBasedIndex)) null
  else {
    val projectDirtyFilesQueue = PersistentDirtyFilesQueue.readProjectDirtyFilesQueue(project.getQueueFile(), ManagingFS.getInstance().creationTimestamp)
    val orphanDirtyFileIds = fileBasedIndex.orphanDirtyFileIds
    val projectDirtyFilesFromOrphanQueue = findProjectFiles(project, orphanDirtyFileIds?.getNotSeenIds(project, projectDirtyFilesQueue) ?: emptyList())
    val allProjectDirtyFileIds = projectDirtyFilesQueue.fileIds + projectDirtyFilesFromOrphanQueue.mapNotNull { (it as? VirtualFileWithId)?.id }
    fileBasedIndex.ensureDirtyFileIndexesDeleted(allProjectDirtyFileIds)
    removeCurrentFile(project.getQueueFile())

    val vfToFindLimit = if (findAllVirtualFiles) -1
    else max(0, dumbModeThreshold - projectDirtyFilesFromOrphanQueue.size - 1)
    
    val projectDirtyFilesFromProjectQueue = findProjectFiles(project, projectDirtyFilesQueue.fileIds, vfToFindLimit)
    val projectDirtyFiles = projectDirtyFilesFromProjectQueue + projectDirtyFilesFromOrphanQueue
    scheduleForIndexing(projectDirtyFiles, fileBasedIndex, dumbModeThreshold - 1)
    ResultOfClearIndexesForDirtyFiles(projectDirtyFilesFromProjectQueue, projectDirtyFilesFromOrphanQueue)
  }
}

private fun OrphanDirtyFilesQueue.getNotSeenIds(project: Project, projectQueue: ProjectDirtyFilesQueue): Collection<Int> {
  if (projectQueue.lastSeenIndexInOrphanQueue > untrimmedSize) {
    LOG.error("It should not happen that project has seen file id in orphan queue at index larger than number of files that orphan queue ever had. " +
              "projectQueue.lastSeenIdsInOrphanQueue=${projectQueue.lastSeenIndexInOrphanQueue}, orphanQueue.lastId=${untrimmedSize}, " +
              "project=$project")
    return emptyList()
  }

  val untrimmedIndexOfFirstElementInOrphanQueue = untrimmedSize - fileIds.size
  val trimmedIndexOfFirstUnseenElement = (projectQueue.lastSeenIndexInOrphanQueue - untrimmedIndexOfFirstElementInOrphanQueue).toInt()
  if (trimmedIndexOfFirstUnseenElement < 0) {
    LOG.error("Full scanning has to be requested. " +
              "orphanQueue.untrimmedSize=$untrimmedSize, " +
              "orphanQueue.fileIds.size=${fileIds.size}, " +
              "projectQueue.lastSeenIndexInOrphanQueue=${projectQueue.lastSeenIndexInOrphanQueue}")
    return fileIds
  }
  return fileIds.subList(trimmedIndexOfFirstUnseenElement, fileIds.size)
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
          if (file != null && fileBasedIndex.belongsToProjectIndexableFiles(file, project)) file else null
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

private fun isIndexableFilesFilterUpToDate(project: Project, filterHolder: ProjectIndexableFilesFilterHolder): Boolean {
  val unsatisfiedConditions = ReusingPersistentFilterConditions.entries.filter { !it.isUpToDate(project, filterHolder) }
  return if (unsatisfiedConditions.isEmpty()) {
    LOG.info("Persistent indexable files filter is up-to-date for project ${project.name}")
    true
  }
  else {
    LOG.info("Persistent indexable files filter is NOT up-to-date for project ${project.name} because of following unsatisfied conditions: $unsatisfiedConditions")
    false
  }
}

private enum class ReusingPersistentFilterConditions {
  IS_PERSISTENT_FILTER_ENABLED {
    override fun isUpToDate(project: Project, filterHolder: ProjectIndexableFilesFilterHolder): Boolean {
      return usePersistentFilesFilter()
    }
  },
  IS_FILTER_LOADED_FROM_DISK {
    override fun isUpToDate(project: Project, filterHolder: ProjectIndexableFilesFilterHolder): Boolean {
      return filterHolder.wasDataLoadedFromDisk(project)
    }
  },
  IS_SCANNING_COMPLETED {
    override fun isUpToDate(project: Project, filterHolder: ProjectIndexableFilesFilterHolder): Boolean {
      return project.getService(ProjectIndexingDependenciesService::class.java).isScanningCompleted()
    }
  },
  FILTER_IS_NOT_INVALIDATED {
    override fun isUpToDate(project: Project, filterHolder: ProjectIndexableFilesFilterHolder): Boolean {
      return project.getUserData(PERSISTENT_INDEXABLE_FILES_FILTER_INVALIDATED) != true
    }
  },
  INDEXING_REQUEST_ID_DID_NOT_CHANGE_AFTER_LAST_SCANNING {
    override fun isUpToDate(project: Project, filterHolder: ProjectIndexableFilesFilterHolder): Boolean {
      val projectService = project.getService(ProjectIndexingDependenciesService::class.java)
      val appService = ApplicationManager.getApplication().getService(AppIndexingDependenciesService::class.java)
      return appService.getCurrent().toInt() == projectService.getAppIndexingRequestIdOfLastScanning()
    }
  };

  abstract fun isUpToDate(project: Project, filterHolder: ProjectIndexableFilesFilterHolder): Boolean
}

private val PERSISTENT_INDEXABLE_FILES_FILTER_INVALIDATED = Key<Boolean>("PERSISTENT_INDEXABLE_FILES_FILTER_INVALIDATED")

internal fun invalidatePersistentIndexableFilesFilter(project: Project) {
  project.putUserData(PERSISTENT_INDEXABLE_FILES_FILTER_INVALIDATED, true)
}

private class ResultOfClearIndexesForDirtyFiles(val projectDirtyFilesFromProjectQueue: List<VirtualFile>, val projectDirtyFilesFromOrphanQueue: List<VirtualFile>)