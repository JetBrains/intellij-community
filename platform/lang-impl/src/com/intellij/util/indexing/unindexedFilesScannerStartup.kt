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
import com.intellij.util.indexing.InitialScanningSkipReporter.FullScanningReason
import com.intellij.util.indexing.InitialScanningSkipReporter.FullScanningReason.*
import com.intellij.util.indexing.InitialScanningSkipReporter.NotSeenIdsBasedFullScanningDecision
import com.intellij.util.indexing.InitialScanningSkipReporter.NotSeenIdsBasedFullScanningDecision.*
import com.intellij.util.indexing.InitialScanningSkipReporter.SourceOfScanning
import com.intellij.util.indexing.ReusingPersistentFilterCondition.IS_FILTER_LOADED_FROM_DISK
import com.intellij.util.indexing.UnindexedFilesScanner.Companion.LOG
import com.intellij.util.indexing.dependencies.AppIndexingDependenciesService
import com.intellij.util.indexing.dependencies.AppIndexingDependenciesToken
import com.intellij.util.indexing.dependencies.ProjectIndexingDependenciesService
import com.intellij.util.indexing.diagnostic.ScanningType
import com.intellij.util.indexing.events.FileIndexingRequest.Companion.updateRequest
import com.intellij.util.indexing.projectFilter.ProjectIndexableFilesFilterHolder
import com.intellij.util.indexing.projectFilter.usePersistentFilesFilter
import kotlinx.coroutines.*
import kotlinx.coroutines.future.asCompletableFuture
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.max

@JvmField
internal val FIRST_SCANNING_REQUESTED: Key<FirstScanningState> = Key.create("FIRST_SCANNING_REQUESTED")
private val dumbModeThreshold: Int
  get() = Registry.intValue("scanning.dumb.mode.threshold", 20)

internal enum class FirstScanningState {
  REQUESTED, PERFORMED
}

private val initialScanningLock = ReentrantLock()
private val PERSISTENT_INDEXABLE_FILES_FILTER_INVALIDATED = Key<Boolean>("PERSISTENT_INDEXABLE_FILES_FILTER_INVALIDATED")

internal fun scanAndIndexProjectAfterOpen(project: Project,
                                          orphanQueue: OrphanDirtyFilesQueue,
                                          additionalOrphanDirtyFiles: Collection<Int>,
                                          projectDirtyFilesQueue: ProjectDirtyFilesQueue,
                                          allowSkippingFullScanning: Boolean,
                                          requireReadingIndexableFilesIndexFromDisk: Boolean,
                                          coroutineScope: CoroutineScope,
                                          indexingReason: String,
                                          fullScanningType: ScanningType,
                                          partialScanningType: ScanningType,
                                          registeredIndexesWereCorrupted: Boolean,
                                          sourceOfScanning: SourceOfScanning): Job {
  FileBasedIndex.getInstance().loadIndexes()
  val isFilterInvalidated = initialScanningLock.withLock {
    (project as UserDataHolderEx).putUserDataIfAbsent(FIRST_SCANNING_REQUESTED, FirstScanningState.REQUESTED)
    project.getUserData(PERSISTENT_INDEXABLE_FILES_FILTER_INVALIDATED) == true
  }
  val fileBasedIndex = FileBasedIndex.getInstance() as FileBasedIndexImpl

  val filterHolder = fileBasedIndex.indexableFilesFilterHolder
  val appCurrent = ApplicationManager.getApplication().getService(AppIndexingDependenciesService::class.java).getCurrent()
  val filterCheckState = FilterCheckState(project, filterHolder, isFilterInvalidated, appCurrent)
  val filterUpToDateUnsatisfiedConditions = findFilterUpToDateUnsatisfiedConditions(filterCheckState, requireReadingIndexableFilesIndexFromDisk)

  val notSeenIds = orphanQueue.getNotSeenIds(project, projectDirtyFilesQueue)
  val scanningCheckState = SkippingScanningCheckState(allowSkippingFullScanning, filterUpToDateUnsatisfiedConditions, notSeenIds)
  val skippingScanningUnsatisfiedConditions = SkippingFullScanningCondition.entries.filter { !it.canSkipFullScanning(scanningCheckState) }
  return if (skippingScanningUnsatisfiedConditions.isEmpty()) {
    LOG.info("Full scanning on startup will be skipped for project ${project.name}")
    val allNotSeenIds = (notSeenIds as AllNotSeenDirtyFileIds).result.plus(additionalOrphanDirtyFiles)
    InitialScanningSkipReporter.reportPartialInitialScanningScheduled(project, sourceOfScanning, projectDirtyFilesQueue, allNotSeenIds.size)
    scheduleDirtyFilesScanning(project, allNotSeenIds, projectDirtyFilesQueue, coroutineScope, indexingReason, partialScanningType)
  }
  else {
    LOG.info("Full scanning on startup will NOT be skipped for project ${project.name} because of following unsatisfied conditions:\n" +
             skippingScanningUnsatisfiedConditions.joinToString("\n") { "${it.name}: ${it.explain(scanningCheckState)}" })
    val reasonsForFullScanning = skippingScanningUnsatisfiedConditions.flatMap { it.getFullScanningReasons(scanningCheckState) }
    val allNotSeenIds = if (notSeenIds is AllNotSeenDirtyFileIds) notSeenIds.result.plus(additionalOrphanDirtyFiles) else additionalOrphanDirtyFiles
    InitialScanningSkipReporter.reportFullInitialScanningScheduled(project,
                                                                   sourceOfScanning,
                                                                   registeredIndexesWereCorrupted,
                                                                   reasonsForFullScanning,
                                                                   scanningCheckState.notSeenIds.getFullScanningDecision(),
                                                                   projectDirtyFilesQueue,
                                                                   allNotSeenIds.size)
    scheduleFullScanning(project, notSeenIds, additionalOrphanDirtyFiles, projectDirtyFilesQueue,
                         filterUpToDateUnsatisfiedConditions.isEmpty(), coroutineScope, indexingReason, fullScanningType)
  }
}


internal fun isFirstProjectScanningRequested(project: Project): Boolean {
  return project.getUserData(FIRST_SCANNING_REQUESTED) != null
}

internal fun isFirstProjectScanningPerformed(project: Project): Boolean {
  return project.getUserData(FIRST_SCANNING_REQUESTED) == FirstScanningState.PERFORMED
}

internal fun invalidateProjectFilterIfFirstScanningNotRequested(project: Project): Boolean {
  return initialScanningLock.withLock {
    if (isFirstProjectScanningRequested(project)) {
      false
    }
    else {
      setProjectFilterIsInvalidated(project, true)
      true
    }
  }
}

internal fun setProjectFilterIsInvalidated(project: Project, invalid: Boolean) {
  project.putUserData(PERSISTENT_INDEXABLE_FILES_FILTER_INVALIDATED, if (invalid) true else null)
}

internal fun Job.forgetProjectDirtyFilesOnCompletion(fileBasedIndex: FileBasedIndexImpl,
                                                     project: Project,
                                                     projectDirtyFilesQueue: ProjectDirtyFilesQueue,
                                                     orphanQueueUntrimmedSize: Long) {
  invokeOnCompletion { e ->
    if (e != null) return@invokeOnCompletion
    fileBasedIndex.dirtyFiles.getProjectDirtyFiles(project)?.removeFiles(projectDirtyFilesQueue.fileIds)
    fileBasedIndex.setLastSeenIndexInOrphanQueue(project, orphanQueueUntrimmedSize)
  }
}

private fun scheduleFullScanning(
  project: Project,
  notSeenIds: GetNotSeenDirtyFileIdsResult,
  additionalOrphanDirtyFiles: Collection<Int>,
  projectDirtyFilesQueue: ProjectDirtyFilesQueue,
  isFilterUpToDate: Boolean,
  coroutineScope: CoroutineScope,
  indexingReason: String,
  fullScanningType: ScanningType
): Job {
  val someDirtyFilesScheduledForIndexing = if (notSeenIds is AllNotSeenDirtyFileIds) coroutineScope.async(Dispatchers.IO) {
    clearIndexesForDirtyFiles(project, notSeenIds.result.plus(additionalOrphanDirtyFiles), projectDirtyFilesQueue, false)
  }
  else CompletableDeferred(Unit)

  UnindexedFilesScanner(project, true, isFilterUpToDate, null, null, indexingReason,
                        fullScanningType, someDirtyFilesScheduledForIndexing.asCompletableFuture(),
                        allowCheckingForOutdatedIndexesUsingFileModCount = notSeenIds !is AllNotSeenDirtyFileIds)
    .queue()
  return someDirtyFilesScheduledForIndexing
}

private fun isShutdownPerformedForFileBasedIndex(fileBasedIndex: FileBasedIndexImpl) =
  fileBasedIndex.registeredIndexes?.isShutdownPerformed ?: true

private fun scheduleDirtyFilesScanning(
  project: Project,
  allNotSeenIds: List<Int>,
  projectDirtyFilesQueue: ProjectDirtyFilesQueue,
  coroutineScope: CoroutineScope,
  indexingReason: String,
  partialScanningType: ScanningType
): Job {
  val projectDirtyFiles = coroutineScope.async(Dispatchers.IO) {
    clearIndexesForDirtyFiles(project, allNotSeenIds, projectDirtyFilesQueue, true)
  }
  val projectDirtyFilesFromProjectQueue = coroutineScope.async { projectDirtyFiles.await()?.projectDirtyFilesFromProjectQueue ?: emptyList() }
  val projectDirtyFilesFromOrphanQueue = coroutineScope.async { projectDirtyFiles.await()?.projectDirtyFilesFromOrphanQueue ?: emptyList() }
  val iterators = listOf(DirtyFilesIndexableFilesIterator(projectDirtyFilesFromProjectQueue, false),
                         DirtyFilesIndexableFilesIterator(projectDirtyFilesFromOrphanQueue, true))
  UnindexedFilesScanner(project, true, true, iterators, null,
                        indexingReason, partialScanningType, projectDirtyFiles.asCompletableFuture())
    .queue()
  return projectDirtyFiles
}

private suspend fun clearIndexesForDirtyFiles(project: Project,
                                              notSeenIds: Collection<Int>,
                                              projectDirtyFilesQueue: ProjectDirtyFilesQueue,
                                              findAllVirtualFiles: Boolean): ResultOfClearIndexesForDirtyFiles? {
  val fileBasedIndex = FileBasedIndex.getInstance() as FileBasedIndexImpl
  return if (isShutdownPerformedForFileBasedIndex(fileBasedIndex)) null
  else {
    val projectDirtyFilesFromOrphanQueue = findProjectFiles(project, notSeenIds)
    val allProjectDirtyFileIds = projectDirtyFilesQueue.fileIds + projectDirtyFilesFromOrphanQueue.mapNotNull { (it as? VirtualFileWithId)?.id }
    fileBasedIndex.ensureDirtyFileIndexesDeleted(allProjectDirtyFileIds)

    val vfToFindLimit = if (findAllVirtualFiles) -1
    else max(0, dumbModeThreshold - projectDirtyFilesFromOrphanQueue.size - 1)
    
    val projectDirtyFilesFromProjectQueue = findProjectFiles(project, projectDirtyFilesQueue.fileIds, vfToFindLimit)
    val projectDirtyFiles = projectDirtyFilesFromProjectQueue + projectDirtyFilesFromOrphanQueue
    scheduleForIndexing(projectDirtyFiles, project, fileBasedIndex, dumbModeThreshold - 1)
    ResultOfClearIndexesForDirtyFiles(projectDirtyFilesFromProjectQueue, projectDirtyFilesFromOrphanQueue)
  }
}

private fun OrphanDirtyFilesQueue.getNotSeenIds(project: Project, projectQueue: ProjectDirtyFilesQueue): GetNotSeenDirtyFileIdsResult {
  if (projectQueue.lastSeenIndexInOrphanQueue > untrimmedSize) {
    LOG.error("It should not happen that project has seen file id in orphan queue at index larger than number of files that orphan queue ever had. " +
              "projectQueue.lastSeenIdsInOrphanQueue=${projectQueue.lastSeenIndexInOrphanQueue}, orphanQueue.lastId=${untrimmedSize}, " +
              "project=$project")
    return ProjectDirtyFilesQueuePointsToIncorrectPosition
  }

  val untrimmedIndexOfFirstElementInOrphanQueue = untrimmedSize - fileIds.size
  val trimmedIndexOfFirstUnseenElement = (projectQueue.lastSeenIndexInOrphanQueue - untrimmedIndexOfFirstElementInOrphanQueue).toInt()
  if (trimmedIndexOfFirstUnseenElement < 0) {
    return DirtyFileIdsWereMissed(this, projectQueue)
  }
  return AllNotSeenDirtyFileIds(fileIds.subList(trimmedIndexOfFirstUnseenElement, fileIds.size))
}

private sealed interface GetNotSeenDirtyFileIdsResult {
  fun explain(): String
  fun getFullScanningDecision(): NotSeenIdsBasedFullScanningDecision
}

private data object ProjectDirtyFilesQueuePointsToIncorrectPosition : GetNotSeenDirtyFileIdsResult {
  override fun explain(): String {
    return "Project dirty files queue points to an index in orphan queue at index larger than number of files that orphan queue ever had"
  }
  override fun getFullScanningDecision(): NotSeenIdsBasedFullScanningDecision = NoSkipDirtyFileQueuePintsToIncorrectPosition
}

private class DirtyFileIdsWereMissed(val orphanDirtyFilesQueue: OrphanDirtyFilesQueue, val projectQueue: ProjectDirtyFilesQueue) : GetNotSeenDirtyFileIdsResult {
  override fun explain(): String {
    return "There are file ids that project missed: orphanQueue.untrimmedSize=${orphanDirtyFilesQueue.untrimmedSize}, " +
           "orphanQueue.fileIds.size=${orphanDirtyFilesQueue.fileIds.size}, " +
           "projectQueue.lastSeenIndexInOrphanQueue=${projectQueue.lastSeenIndexInOrphanQueue}"
  }
  override fun getFullScanningDecision(): NotSeenIdsBasedFullScanningDecision = NoSkipDirtyFileIdsWereMissed
}

private class AllNotSeenDirtyFileIds(val result: Collection<Int>) : GetNotSeenDirtyFileIdsResult {
  override fun explain(): String = "All not seen ids are known: $result"
  override fun getFullScanningDecision(): NotSeenIdsBasedFullScanningDecision = DirtyFileIdsCompatibleWithFullScanningSkip
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

private suspend fun scheduleForIndexing(someProjectDirtyFilesFiles: List<VirtualFile>, project: Project, fileBasedIndex: FileBasedIndexImpl, limit: Int) {
  readActionBlocking {
    for (file in someProjectDirtyFilesFiles.run { if (limit > 0) take(limit) else this }) {
      fileBasedIndex.filesToUpdateCollector.scheduleForUpdate(updateRequest(file), setOf(project), emptyList())
    }
  }
}

private fun findFilterUpToDateUnsatisfiedConditions(state: FilterCheckState, requireReadingIndexableFilesIndexFromDisk: Boolean): List<ReusingPersistentFilterCondition> {
  return ReusingPersistentFilterCondition.entries
    .filter { !it.isUpToDate(state) }
    .let { conditions ->
      if (requireReadingIndexableFilesIndexFromDisk) conditions
      else conditions.filter { it != IS_FILTER_LOADED_FROM_DISK }
    }
}

private class FilterCheckState(val project: Project,
                               val filterHolder: ProjectIndexableFilesFilterHolder,
                               val isFilterInvalidated: Boolean,
                               val appCurrent: AppIndexingDependenciesToken)

private enum class ReusingPersistentFilterCondition(val reason: FullScanningReason) {
  IS_PERSISTENT_FILTER_ENABLED(FilterIncompatibleAsPersistentFilterIsDisabled) {
    override fun isUpToDate(state: FilterCheckState): Boolean {
      return usePersistentFilesFilter()
    }
  },
  IS_FILTER_LOADED_FROM_DISK(FilterIncompatibleAsNotLoadedFromDisc) {
    override fun isUpToDate(state: FilterCheckState): Boolean {
      return state.filterHolder.wasDataLoadedFromDisk(state.project)
    }
  },
  IS_SCANNING_AND_INDEXING_COMPLETED(FilterIncompatibleAsFullScanningIsNotCompleted) {
    override fun isUpToDate(state: FilterCheckState): Boolean {
      return state.project.getService(ProjectIndexingDependenciesService::class.java).isScanningAndIndexingCompleted()
    }
  },
  FILTER_IS_NOT_INVALIDATED(FilterIncompatibleAsFilterIsInvalidated) {
    override fun isUpToDate(state: FilterCheckState): Boolean {
      return !state.isFilterInvalidated
    }
  },
  INDEXING_REQUEST_ID_DID_NOT_CHANGE_AFTER_LAST_SCANNING(FilterIncompatibleAsAppIndexingRequestIdChangedSinceLastScanning) {
    override fun isUpToDate(state: FilterCheckState): Boolean {
      val projectService = state.project.getService(ProjectIndexingDependenciesService::class.java)
      return state.appCurrent.toInt() == projectService.getAppIndexingRequestIdOfLastScanning()
    }
  };

  abstract fun isUpToDate(state: FilterCheckState): Boolean
}

private class SkippingScanningCheckState(val allowSkippingFullScanning: Boolean,
                                         val filterUpToDateUnsatisfiedConditions: List<ReusingPersistentFilterCondition>,
                                         val notSeenIds: GetNotSeenDirtyFileIdsResult)

private enum class SkippingFullScanningCondition {
  ALLOWED {
    override fun canSkipFullScanning(state: SkippingScanningCheckState): Boolean = state.allowSkippingFullScanning
    override fun explain(state: SkippingScanningCheckState): String = "Full scanning was requested"
    override fun getFullScanningReasons(state: SkippingScanningCheckState): List<FullScanningReason> = listOf(CodeCallerForbadeSkipping)
  },
  FILTER_IS_NOT_UP_TO_DATE {
    override fun canSkipFullScanning(state: SkippingScanningCheckState): Boolean = state.filterUpToDateUnsatisfiedConditions.isEmpty()
    override fun explain(state: SkippingScanningCheckState): String = "Persistent indexable files filter is NOT up-to-date because of following unsatisfied conditions: ${state.filterUpToDateUnsatisfiedConditions}"
    override fun getFullScanningReasons(state: SkippingScanningCheckState): List<FullScanningReason> =
      state.filterUpToDateUnsatisfiedConditions.map {
        it.reason
      }
  },
  REGISTRY_FILE_IS_ON {
    override fun canSkipFullScanning(state: SkippingScanningCheckState): Boolean = Registry.`is`("full.scanning.on.startup.can.be.skipped")
    override fun explain(state: SkippingScanningCheckState): String = "Registry flag 'full.scanning.on.startup.can.be.skipped' is turned off"
    override fun getFullScanningReasons(state: SkippingScanningCheckState): List<FullScanningReason> = listOf(RegistryForbadeSkipping)
  },
  DIRTY_FILE_IDS_WERE_MISSED {
    override fun canSkipFullScanning(state: SkippingScanningCheckState): Boolean = state.notSeenIds is AllNotSeenDirtyFileIds
    override fun explain(state: SkippingScanningCheckState): String = state.notSeenIds.explain()
    override fun getFullScanningReasons(state: SkippingScanningCheckState): List<FullScanningReason> = emptyList()
  };

  abstract fun canSkipFullScanning(state: SkippingScanningCheckState): Boolean
  abstract fun explain(state: SkippingScanningCheckState): String
  abstract fun getFullScanningReasons(state: SkippingScanningCheckState): List<FullScanningReason>
}

private class ResultOfClearIndexesForDirtyFiles(val projectDirtyFilesFromProjectQueue: List<VirtualFile>, val projectDirtyFilesFromOrphanQueue: List<VirtualFile>)