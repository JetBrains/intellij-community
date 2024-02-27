// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.projectFilter

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import com.intellij.util.indexing.IndexInfrastructure
import com.intellij.util.indexing.projectFilter.HealthCheckErrorType.INDEXABLE_FILE_NOT_IN_FILTER
import com.intellij.util.indexing.projectFilter.HealthCheckErrorType.NON_INDEXABLE_FILE_IN_FILTER
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

      val errorsByType = doRunHealthCheckInReadAction()

      errorsByType.fix(filter)

      IndexableFilesFilterHealthCheckCollector.reportIndexableFilesFilterHealthcheck(
        project,
        filter,
        attemptNumber,
        successfulAttemptsCount.incrementAndGet(),
        errorsByType[NON_INDEXABLE_FILE_IN_FILTER]?.size ?: 0,
        errorsByType[INDEXABLE_FILE_NOT_IN_FILTER]?.size ?: 0)

      for ((errorType, errors) in errorsByType) {
        if (errors.isEmpty()) continue

        val message = "${errorType.message}. Errors count: ${errors.size}. Examples:\n" +
                      errors.joinToString("\n", limit = 5) { error ->
                        ReadAction.nonBlocking(Callable { error.fileInfo() }).executeSynchronously()
                      }
        errorType.logger(message)
      }
    }
    catch (_: ProcessCanceledException) {

    }
    catch (e: Exception) {
      LOG.error(e)
    }
  }

  private fun doRunHealthCheckInReadAction(): Map<HealthCheckErrorType, List<FileId>> {
    return ReadAction.nonBlocking(::doRunHealthCheck).inSmartMode(project).executeSynchronously()
  }

  private fun doRunHealthCheck(): Map<HealthCheckErrorType, List<FileId>> {
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
                               fileStatuses: Sequence<Pair<FileId, Boolean>>): Map<HealthCheckErrorType, List<FileId>> {
    val nonIndexableFilesInFilter = mutableListOf<FileId>()
    val indexableFilesNotInFilter = mutableListOf<FileId>()

    val shouldBeIndexable = getFilesThatShouldBeIndexable(project)

    for ((fileId, isInFilter) in fileStatuses) {
      ProgressManager.checkCanceled()
      if (shouldBeIndexable[fileId]) {
        if (!isInFilter) {
          indexableFilesNotInFilter.add(fileId)
        }
        if (checkAllExpectedIndexableFiles) shouldBeIndexable[fileId] = false
      }
      else if (isInFilter && !shouldBeIndexable[fileId]) {
        nonIndexableFilesInFilter.add(fileId)
      }
    }

    if (checkAllExpectedIndexableFiles) {
      for (fileId in 0 until shouldBeIndexable.size()) {
        if (shouldBeIndexable[fileId]) {
          indexableFilesNotInFilter.add(fileId)
        }
      }
    }

    return mapOf(NON_INDEXABLE_FILE_IN_FILTER to nonIndexableFilesInFilter,
                 INDEXABLE_FILE_NOT_IN_FILTER to indexableFilesNotInFilter)
  }

  private fun getFilesThatShouldBeIndexable(project: Project): BitSet {
    val index = FileBasedIndex.getInstance() as FileBasedIndexImpl
    val filesThatShouldBeIndexable = BitSet()
    index.iterateIndexableFiles(ContentIterator {
      if (it is VirtualFileWithId) {
        ProgressManager.checkCanceled()
        filesThatShouldBeIndexable[it.id] = true
      }
      true
    }, project, ProgressManager.getInstance().progressIndicator)
    return filesThatShouldBeIndexable
  }

  fun stopHealthCheck() {
    healthCheckFuture?.cancel(true)
    healthCheckFuture = null
  }
}

internal typealias HealthCheckLogger = (String) -> Unit

internal val infoLogger: HealthCheckLogger = { LOG.info(it) }
internal val warnLogger: HealthCheckLogger = { LOG.warn(it) }

internal fun Map<HealthCheckErrorType, List<FileId>>.fix(filter: ProjectIndexableFilesFilter) {
  this.forEach { (type, files) ->
    files.forEach { file -> type.fix(file, filter) }
  }
}

internal enum class HealthCheckErrorType(val message: String, val logger: HealthCheckLogger) {
  NON_INDEXABLE_FILE_IN_FILTER("Following files are NOT indexable but they were found in filter", infoLogger) {
    override fun fix(fileId: FileId, filter: ProjectIndexableFilesFilter) {
      filter.removeFileId(fileId)
    }
  },
  INDEXABLE_FILE_NOT_IN_FILTER("Following files are indexable but they were NOT found in filter", warnLogger) {
    override fun fix(fileId: FileId, filter: ProjectIndexableFilesFilter) {
      filter.ensureFileIdPresent(fileId) { true }
    }
  };

  abstract fun fix(fileId: FileId, filter: ProjectIndexableFilesFilter)
}

