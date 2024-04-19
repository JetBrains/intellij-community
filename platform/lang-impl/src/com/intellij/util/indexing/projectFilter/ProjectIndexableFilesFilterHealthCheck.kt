// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.projectFilter

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.platform.backend.observation.Observation
import com.intellij.util.SystemProperties
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import com.intellij.util.indexing.IndexInfrastructure
import com.intellij.util.indexing.roots.IndexableFilesIterator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val LOG = Logger.getInstance(ProjectIndexableFilesFilterHealthCheck::class.java)

internal typealias FileId = Int
private fun FileId.fileInfo(): String = "file id=$this path=${PersistentFS.getInstance().findFileById(this)?.path}"

private class ProjectIndexableFilesFilterHealthCheckStarter : ProjectActivity {
  init {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    val delay = if (ApplicationManagerEx.isInIntegrationTest()) 1.minutes else 5.minutes
    // don't get service too early
    delay(delay)
    val healthCheck = project.serviceAsync<ProjectIndexableFilesFilterHealthCheck>()
    while (true) {
      healthCheck.launchHealthCheck()
      delay(delay)
    }
  }
}

@Service(Service.Level.PROJECT)
class ProjectIndexableFilesFilterHealthCheck(private val project: Project, private val coroutineScope: CoroutineScope) {
  private val isRunning = AtomicBoolean()
  private val attemptsCount = AtomicInteger()
  private val cancelledAttemptsCount = AtomicInteger()
  private val successfulAttemptsCount = AtomicInteger()

  fun launchHealthCheck() {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return
    }

    coroutineScope.launch {
      if (!isRunning.compareAndSet(false, true)) {
        return@launch
      }
      try {
        runHealthCheck()
      }
      finally {
        isRunning.set(false)
      }
    }
  }

  /**
   * [ProjectIndexableFilesFilter] is compared to set of files from [FileBasedIndexImpl.iterateIndexableFiles] (all files that
   * should belong to workspace)
   *
   * There could be two possible types of inconsistency between [ProjectIndexableFilesFilter] and [FileBasedIndexImpl.iterateIndexableFiles]:
   * 1. False positives - files that were found in filter but were NOT iterated by [FileBasedIndexImpl.iterateIndexableFiles].
   *    This is fine because files can be removed from workspace, but we clear indexes for them lazily
   * 2. False negatives - files that were NOT found in filter but were iterated by [FileBasedIndexImpl.iterateIndexableFiles]
   */
  private suspend fun runHealthCheck() {
    if (!IndexInfrastructure.hasIndices()) return
    val filter = (FileBasedIndex.getInstance() as? FileBasedIndexImpl)?.indexableFilesFilterHolder?.getProjectIndexableFiles(project)
                 ?: return

    LOG.runAndLogException {
      val attemptNumber = attemptsCount.incrementAndGet()
      IndexableFilesFilterHealthCheckCollector.reportIndexableFilesFilterHealthcheckStarted(project, filter, attemptNumber)
      val startTime = System.currentTimeMillis()

      Observation.awaitConfiguration(project) // wait for project import IDEA-348501
      val res: HealthCheckResult = smartReadActionWithDelays(5.seconds, 20) {
        try {
          runHealthCheck(project, filter)
        }
        catch (e: FilterActionCancelledException) {
          HealthCheckCancelled(e.reason)
        }
      }
      when (res) {
        is HealthCheckFinished -> {
          res.nonIndexableFilesInFilter.fix(filter)
          res.indexableFilesNotInFilter.fix(filter)

          IndexableFilesFilterHealthCheckCollector.reportIndexableFilesFilterHealthcheck(
            project,
            filter,
            attemptNumber,
            successfulAttemptsCount.incrementAndGet(),
            (System.currentTimeMillis() - startTime).toInt(),
            res.nonIndexableFilesInFilter.size,
            res.indexableFilesNotInFilter.size)

          res.nonIndexableFilesInFilter.logMessage()
          res.indexableFilesNotInFilter.logMessage()
        }
        is HealthCheckCancelled -> {
          IndexableFilesFilterHealthCheckCollector.reportIndexableFilesFilterHealthcheckCancelled(
            project,
            filter,
            attemptNumber,
            cancelledAttemptsCount.incrementAndGet(),
            (System.currentTimeMillis() - startTime).toInt(),
            res.reason)
        }
      }
    }
  }

  private suspend fun smartReadActionWithDelays(delay: Duration, attemptsAtATime: Int, action: () -> HealthCheckResult): HealthCheckResult {
    while (true) {
      val res = smartReadActionWithMaxAttempts(attemptsAtATime, action)
      if (res != null) {
        return res
      }
      delay(delay) // allow a batch of write actions to finish
    }
  }

  private suspend fun smartReadActionWithMaxAttempts(maxAttemptsCount: Int, action: () -> HealthCheckResult): HealthCheckResult? {
    val attemptsCount = AtomicInteger(0)

    return smartReadAction(project) {
      if (attemptsCount.getAndIncrement() > maxAttemptsCount) null
      else action()
    }
  }

  private fun runHealthCheck(project: Project, filter: ProjectIndexableFilesFilter): HealthCheckResult {
    return filter.runAndCheckThatNoChangesHappened {
      runIfScanningScanningIsCompleted(project) {
        // It is possible that scanning will start and finish while we are performing healthcheck,
        // but then healthcheck will be terminated by the fact that filter was update.
        // If it was not updated, then we don't care that scanning happened, and we can trust healthcheck result
        doRunHealthCheck(project, filter.checkAllExpectedIndexableFilesDuringHealthcheck, filter.getFileStatuses())
      }
    }
  }

  /**
   * This healthcheck makes the most sense for [com.intellij.util.indexing.projectFilter.IncrementalProjectIndexableFilesFilter]
   * because it's filled during scanning which iterates over [com.intellij.util.indexing.roots.IndexableFilesIterator]s
   * so if we iterate over IndexableFilesIterator again, it should match filter.
   * Such errors would mean that we missed some event when a file became (un)indexed e.g., when it was deleted or marked excluded.
   *
   * Errors reported for [com.intellij.util.indexing.projectFilter.CachingProjectIndexableFilesFilter] could also mean an inconsistency
   * between [com.intellij.util.indexing.roots.IndexableFilesIterator] and [com.intellij.util.indexing.IndexableFilesIndex].
   */
  private fun doRunHealthCheck(project: Project,
                               checkAllExpectedIndexableFiles: Boolean,
                               fileStatuses: Sequence<Pair<FileId, Boolean>>): HealthCheckFinished {
    val nonIndexableFilesInFilter = mutableListOf<FileId>()
    val indexableFilesNotInFilter = mutableListOf<FileId>()

    val shouldBeIndexable = getFilesThatShouldBeIndexable(project)
    val filesInFilter = BitSet()

    for ((fileId, isInFilter) in fileStatuses) { // Sequence instead of BitSet because we need to distinguish false and null
      ProgressManager.checkCanceled()
      filesInFilter[fileId] = isInFilter
      if (shouldBeIndexable[fileId]) {
        if (!isInFilter) {
          indexableFilesNotInFilter.add(fileId)
        }
      }
      else if (isInFilter && !shouldBeIndexable[fileId]) {
        nonIndexableFilesInFilter.add(fileId)
      }
    }

    if (checkAllExpectedIndexableFiles) {
      for (fileId in 0 until shouldBeIndexable.size) {
        if (shouldBeIndexable[fileId] && !filesInFilter[fileId]) {
          indexableFilesNotInFilter.add(fileId)
        }
      }
    }

    return HealthCheckFinished(NonIndexableFilesInFilterGroup(nonIndexableFilesInFilter),
                               IndexableFilesNotInFilterGroup(indexableFilesNotInFilter, shouldBeIndexable))
  }

  private fun getFilesThatShouldBeIndexable(project: Project): IndexableFiles {
    val indexableFiles = IndexableFiles()
    getFilesThatShouldBeIndexable(project, if (shouldLogProviders) IndexableFilesSetWithProvidersHandler(indexableFiles) else IndexableFilesSetHandler(indexableFiles))
    return indexableFiles
  }

  private fun <T> getFilesThatShouldBeIndexable(project: Project, handler: FilesSetHandler<T>) {
    val index = FileBasedIndex.getInstance() as FileBasedIndexImpl
    val providers = index.getIndexableFilesProviders(project)
    for (provider in providers) {
      val state: T = handler.createStateForProvider()
      val outerProcessor = ContentIterator {
        if (it is VirtualFileWithId) {
          ProgressManager.checkCanceled()
          handler.addToState(state, it.id)
        }
        true
      }
      ProgressManager.checkCanceled()
      provider.iterateFiles(project, outerProcessor, VirtualFileFilter.ALL)
      handler.flushState(state, provider)
    }
  }
}

private interface FilesSetHandler<T> {
  fun createStateForProvider(): T
  fun addToState(state: T, id: FileId)
  fun flushState(state: T, iterator: IndexableFilesIterator)
}

private class IndexableFilesSetWithProvidersHandler(val indexableFiles: IndexableFiles) : FilesSetHandler<BitSet> {
  override fun createStateForProvider(): BitSet = BitSet()
  override fun addToState(state: BitSet, id: FileId) = state.set(id)
  override fun flushState(state: BitSet, iterator: IndexableFilesIterator) = indexableFiles.add(state, iterator)
}

private class IndexableFilesSetHandler(val indexableFiles: IndexableFiles) : FilesSetHandler<Unit> {
  override fun createStateForProvider() = Unit
  override fun addToState(state: Unit, id: FileId) = indexableFiles.add(id)
  override fun flushState(state: Unit, iterator: IndexableFilesIterator) = Unit
}

private sealed class HealthCheckErrorGroup(val fileIds: List<FileId>, val message: String) {
  val size: Int
    get() = fileIds.size

  fun fix(filter: ProjectIndexableFilesFilter) {
    for (fileId in fileIds) {
      fix(fileId, filter)
    }
  }

  suspend fun logMessage() {
    if (fileIds.isEmpty()) return

    val message = readAction {
      "${message}. Errors count: ${fileIds.size}. Examples:\n" + fileIds.joinToString("\n", limit = 5) { error ->
        fileInfo(error)
      }
    }
    log(message)
  }

  abstract fun fileInfo(fileId: FileId): String
  abstract fun fix(fileId: FileId, filter: ProjectIndexableFilesFilter)
  abstract fun log(message: String)
}

private class NonIndexableFilesInFilterGroup(files: List<FileId>) : HealthCheckErrorGroup(files, "Following files are NOT indexable but they were found in filter") {
  override fun fix(fileId: FileId, filter: ProjectIndexableFilesFilter) {
    filter.removeFileId(fileId)
  }

  override fun log(message: String) {
    LOG.info(message)
  }

  override fun fileInfo(fileId: FileId): String {
    return fileId.fileInfo()
  }
}

private val shouldLogProviders = SystemProperties.getBooleanProperty("project.indexable.files.filter.health.check.log.provider", false) // may consume too much memory

private class IndexableFilesNotInFilterGroup(files: List<FileId>, private val shouldBeIndexableFiles: IndexableFiles) : HealthCheckErrorGroup(files, "Following files are indexable but they were NOT found in filter") {
  override fun fix(fileId: FileId, filter: ProjectIndexableFilesFilter) {
    filter.ensureFileIdPresent(fileId) { true }
  }

  override fun log(message: String) {
    LOG.warn(message)
  }

  override fun fileInfo(fileId: FileId): String {
    return if (shouldLogProviders) "${fileId.fileInfo()} provider=${shouldBeIndexableFiles.getProvider(fileId)?.debugName}"
    else fileId.fileInfo()
  }
}

private class IndexableFiles {
  private val allFiles: BitSet = BitSet()
  private val perProvider: MutableList<Pair<IndexableFilesIterator, BitSet>> = mutableListOf()

  val size = allFiles.size()

  fun add(fileId: FileId) {
    allFiles.set(fileId)
  }

  fun add(fileSet: BitSet, provider: IndexableFilesIterator) {
    allFiles.or(fileSet)
    perProvider.add(Pair(provider, fileSet))
  }

  operator fun get(fileId: FileId): Boolean {
    return allFiles[fileId]
  }

  fun getProvider(fileId: FileId): IndexableFilesIterator? {
    return perProvider.find { it.second.get(fileId) }?.first
  }
}

sealed interface HealthCheckResult

private class HealthCheckFinished(val nonIndexableFilesInFilter: NonIndexableFilesInFilterGroup, val indexableFilesNotInFilter: IndexableFilesNotInFilterGroup) : HealthCheckResult
private class HealthCheckCancelled(val reason: FilterActionCancellationReason) : HealthCheckResult
