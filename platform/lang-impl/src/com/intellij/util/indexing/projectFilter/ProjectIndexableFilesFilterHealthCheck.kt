// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.projectFilter

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import com.intellij.util.indexing.IndexInfrastructure
import com.intellij.util.indexing.projectFilter.HealthCheckErrorType.INDEXABLE_FILE_NOT_FOUND_IN_FILTER
import com.intellij.util.indexing.projectFilter.HealthCheckErrorType.NON_INDEXABLE_FILE_FOUND_IN_FILTER
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal class ProjectIndexableFilesFilterHealthCheck(private val project: Project, private val filter: ProjectIndexableFilesFilter) {
  @Volatile
  private var healthCheckFuture: ScheduledFuture<*>? = null

  fun setUpHealthCheck() {
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      healthCheckFuture = AppExecutorUtil
        .getAppScheduledExecutorService()
        .scheduleWithFixedDelay(ConcurrencyUtil.underThreadNameRunnable("Index files filter health check for project ${project.name}") {
          runHealthCheck(false)
        }, 5, 5, TimeUnit.MINUTES)
    }
  }

  fun triggerHealthCheck(onProjectOpen: Boolean) {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return
    }

    stopHealthCheck()
    AppExecutorUtil.getAppExecutorService().submit {
      try {
        runHealthCheck(onProjectOpen)
      }
      finally {
        setUpHealthCheck()
      }
    }
  }

  @Synchronized // don't allow two parallel health checks in case of triggerHealthCheck()
  private fun runHealthCheck(onProjectOpen: Boolean) {
    if (!IndexInfrastructure.hasIndices()) return

    try {
      val errors: List<HealthCheckError> = doRunHealthCheckInReadAction() ?: return

      for (error in errors) {
        error.fix()
      }

      val (excludedFilesWereFilteredOut, errorsToReport) = tryToFilterOutExcludedFiles(errors)

      val excludedFilesCount = errors.size - errorsToReport.size
      if (excludedFilesCount > 0) {
        FileBasedIndexImpl.LOG.info("$excludedFilesCount of ${filter.javaClass.simpleName} health check errors were filtered out")
      }

      val errorGroups = errorsToReport
        .groupBy { it.type }

      val nonIndexableFoundInFilterCount = errorGroups.entries.find { it.key == NON_INDEXABLE_FILE_FOUND_IN_FILTER }?.value?.size ?: 0
      val indexableNotFoundInFilterCount = errorGroups.entries.find { it.key == INDEXABLE_FILE_NOT_FOUND_IN_FILTER }?.value?.size ?: 0

      IndexableFilesFilterHealthCheckCollector.reportIndexableFilesFilterHealthcheck(project,
                                                                                     onProjectOpen,
                                                                                     nonIndexableFoundInFilterCount,
                                                                                     indexableNotFoundInFilterCount,
                                                                                     excludedFilesWereFilteredOut,
                                                                                     excludedFilesCount)

      if (errorsToReport.isEmpty()) return

      val summary = errorGroups
        .entries.joinToString("\n") { (type, e) ->
          "${e.size} $type errors. Examples:\n" + e.take(10).joinToString("\n") { error ->
            ReadAction.nonBlocking(Callable { error.presentableText }).executeSynchronously()
          }
        }

      val checkForExcludedFilesInfo = if (excludedFilesWereFilteredOut) "Excluded files were filtered out."
      else "Check for excluded files was skipped, errors might be false-positive."

      FileBasedIndexImpl.LOG.warn("${filter.javaClass.simpleName} health check found ${errorsToReport.size} errors in project ${project.name}.\n" +
                                  "$checkForExcludedFilesInfo\n:" +
                                  summary)
    }
    catch (_: ProcessCanceledException) {

    }
    catch (e: Exception) {
      FileBasedIndexImpl.LOG.error(e)
    }
  }

  private fun tryToFilterOutExcludedFiles(errors: List<HealthCheckError>): Pair<Boolean, List<HealthCheckError>> {
    val checkForExcludedFiles = errors.size <= Registry.intValue("index.files.filter.check.excluded.files.limit")
    if (!checkForExcludedFiles) {
      return Pair(false, errors)
    }
    try {
      return Pair(true, ReadAction.nonBlocking(Callable { filterOutExcludedFiles(errors) }).executeSynchronously())
    }
    catch (ignored: ProcessCanceledException) {
      return Pair(false, errors)
    }
  }

  private fun filterOutExcludedFiles(errors: List<HealthCheckError>): List<HealthCheckError> {
    val projectFileIndex = ProjectFileIndex.getInstance(project)
    return errors.filter {  error ->
      if (error !is NotIndexableFileIsInFilterError) true
      else {
        val file = PersistentFS.getInstance().findFileById(error.fileId)
        file == null || !projectFileIndex.isExcluded(file)
      }
    }
  }

  private fun doRunHealthCheckInReadAction(): List<HealthCheckError>? {
    var errors: List<HealthCheckError>? = null
    ProgressIndicatorUtils.runInReadActionWithWriteActionPriority {
      if (DumbService.isDumb(project)) return@runInReadActionWithWriteActionPriority
      errors = doRunHealthCheck()
    }
    return errors
  }

  private fun doRunHealthCheck(): List<HealthCheckError> {
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
  private fun doRunHealthCheck(project: Project, checkAllExpectedIndexableFiles: Boolean, fileStatuses: Sequence<Pair<Int, Boolean>>): List<HealthCheckError> {
    val errors = mutableListOf<HealthCheckError>()

    val shouldBeIndexable = getFilesThatShouldBeIndexable(project)

    for ((fileId, indexable) in fileStatuses) {
      ProgressManager.checkCanceled()
      if (shouldBeIndexable[fileId]) {
        if (!indexable) {
          errors.add(MissingFileIdInFilterError(fileId, filter))
        }
        if (checkAllExpectedIndexableFiles) shouldBeIndexable[fileId] = false
      }
      else if (indexable && !shouldBeIndexable[fileId]) {
        errors.add(NotIndexableFileIsInFilterError(fileId, filter))
      }
    }

    if (checkAllExpectedIndexableFiles) {
      for (fileId in 0 until shouldBeIndexable.size()) {
        if (shouldBeIndexable[fileId]) {
          errors.add(MissingFileIdInFilterError(fileId, filter))
        }
      }
    }

    return errors
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

enum class HealthCheckErrorType {
  NON_INDEXABLE_FILE_FOUND_IN_FILTER,
  INDEXABLE_FILE_NOT_FOUND_IN_FILTER
}

sealed interface HealthCheckError {
  val presentableText: String
  val type: HealthCheckErrorType
  fun fix()
}

internal class MissingFileIdInFilterError(private val fileId: Int,
                                          private val filter: ProjectIndexableFilesFilter) : HealthCheckError {
  override val type = INDEXABLE_FILE_NOT_FOUND_IN_FILTER
  override val presentableText: String
    get() = "file name=${PersistentFS.getInstance().findFileById(fileId)?.name} id=$fileId NOT found in filter"

  override fun fix() {
    filter.ensureFileIdPresent(fileId) { true }
  }
}

internal class NotIndexableFileIsInFilterError(val fileId: Int,
                                               private val filter: ProjectIndexableFilesFilter) : HealthCheckError {
  override val type = NON_INDEXABLE_FILE_FOUND_IN_FILTER
  override val presentableText: String
    get() = "file name=${
      PersistentFS.getInstance().findFileById(fileId)?.name
    } id=$fileId is found in filter even though it's NOT indexable"

  override fun fix() {
    filter.removeFileId(fileId)
  }
}