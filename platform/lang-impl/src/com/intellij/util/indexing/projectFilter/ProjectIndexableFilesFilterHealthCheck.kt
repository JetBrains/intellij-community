// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.projectFilter

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.vfs.VirtualFileFilter
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import com.intellij.util.indexing.IndexInfrastructure
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.jetbrains.rd.util.AtomicInteger
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit


private val LOG = Logger.getInstance(ProjectIndexableFilesFilterHealthCheck::class.java)

internal typealias FileId = Int
private fun FileId.fileInfo(): String = "file id=$this path=${PersistentFS.getInstance().findFileById(this)?.path}"

internal class ProjectIndexableFilesFilterHealthCheck(private val project: Project, private val filter: ProjectIndexableFilesFilter) {
  private val attemptsCount = AtomicInteger()
  private val successfulAttemptsCount = AtomicInteger()
  @Volatile
  private var healthCheckFuture: ScheduledFuture<*>? = null

  fun setUpHealthCheck() {
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      healthCheckFuture = AppExecutorUtil
        .getAppScheduledExecutorService()
        .scheduleWithFixedDelay(ConcurrencyUtil.underThreadNameRunnable("Index files filter health check for project ${project.name}") {
          runHealthCheck()
        }, 5, 5, TimeUnit.MINUTES)
    }
  }

  fun triggerHealthCheck() {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return
    }

    stopHealthCheck()
    AppExecutorUtil.getAppExecutorService().submit {
      try {
        runHealthCheck()
      }
      finally {
        setUpHealthCheck()
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
  @Synchronized // don't allow two parallel health checks in case of triggerHealthCheck()
  private fun runHealthCheck() {
    if (!IndexInfrastructure.hasIndices()) return

    try {
      val attemptNumber = attemptsCount.incrementAndGet()
      IndexableFilesFilterHealthCheckCollector.reportIndexableFilesFilterHealthcheckStarted(project, filter, attemptNumber)

      val (nonIndexableFilesInFilter, indexableFilesNotInFilter) = doRunHealthCheckInReadAction()

      nonIndexableFilesInFilter.fix(filter)
      indexableFilesNotInFilter.fix(filter)

      IndexableFilesFilterHealthCheckCollector.reportIndexableFilesFilterHealthcheck(
        project,
        filter,
        attemptNumber,
        successfulAttemptsCount.incrementAndGet(),
        nonIndexableFilesInFilter.size,
        indexableFilesNotInFilter.size)

      nonIndexableFilesInFilter.logMessage()
      indexableFilesNotInFilter.logMessage()
    }
    catch (_: ProcessCanceledException) {

    }
    catch (e: Exception) {
      LOG.error(e)
    }
  }

  private fun doRunHealthCheckInReadAction(): Pair<NonIndexableFilesInFilterGroup, IndexableFilesNotInFilterGroup> {
    return ReadAction.nonBlocking(::doRunHealthCheck).inSmartMode(project).executeSynchronously()
  }

  private fun doRunHealthCheck(): Pair<NonIndexableFilesInFilterGroup, IndexableFilesNotInFilterGroup> {
    return runIfScanningScanningIsCompleted(project) {
      filter.runAndCheckThatNoChangesHappened {
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
                               fileStatuses: Sequence<Pair<FileId, Boolean>>): Pair<NonIndexableFilesInFilterGroup, IndexableFilesNotInFilterGroup> {
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

    return NonIndexableFilesInFilterGroup(nonIndexableFilesInFilter) to
      IndexableFilesNotInFilterGroup(indexableFilesNotInFilter, shouldBeIndexable)
  }

  private fun getFilesThatShouldBeIndexable(project: Project): IndexableFiles {
    val indexableFiles = IndexableFiles()
    iterateIndexableFiles(project) { provider, fileSet ->
      indexableFiles.add(fileSet, provider)
    }
    return indexableFiles
  }

  private fun iterateIndexableFiles(project: Project, processor: (IndexableFilesIterator, BitSet) -> Unit) {
    val index = FileBasedIndex.getInstance() as FileBasedIndexImpl
    val providers = index.getIndexableFilesProviders(project)
    for (provider in providers) {
      val set = BitSet()
      val outerProcessor = ContentIterator {
        if (it is VirtualFileWithId) {
          ProgressManager.checkCanceled()
          set.set(it.id)
        }
        true
      }
      ProgressManager.checkCanceled()
      provider.iterateFiles(project, outerProcessor, VirtualFileFilter.ALL)
      processor(provider, set)
    }
  }

  fun stopHealthCheck() {
    healthCheckFuture?.cancel(true)
    healthCheckFuture = null
  }
}

private sealed class HealthCheckErrorGroup(val fileIds: List<FileId>, val message: String) {
  val size: Int
    get() = fileIds.size

  fun fix(filter: ProjectIndexableFilesFilter) {
    for (fileId in fileIds) {
      fix(fileId, filter)
    }
  }

  fun logMessage() {
    if (fileIds.isEmpty()) return

    val message = "${message}. Errors count: ${fileIds.size}. Examples:\n" + fileIds.joinToString("\n", limit = 5) { error ->
      ReadAction.nonBlocking(Callable { fileInfo(error) }).executeSynchronously()
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

private class IndexableFilesNotInFilterGroup(files: List<FileId>, private val shouldBeIndexableFiles: IndexableFiles) : HealthCheckErrorGroup(files, "Following files are indexable but they were NOT found in filter") {
  override fun fix(fileId: FileId, filter: ProjectIndexableFilesFilter) {
    filter.ensureFileIdPresent(fileId) { true }
  }

  override fun log(message: String) {
    LOG.warn(message)
  }

  override fun fileInfo(fileId: FileId): String {
    return "${fileId.fileInfo()} provider=${shouldBeIndexableFiles.getProvider(fileId)?.debugName}"
  }
}

private class IndexableFiles {
  private val allFiles: BitSet = BitSet()
  private val perProvider: MutableList<Pair<IndexableFilesIterator, BitSet>> = mutableListOf()

  val size = allFiles.size()

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
