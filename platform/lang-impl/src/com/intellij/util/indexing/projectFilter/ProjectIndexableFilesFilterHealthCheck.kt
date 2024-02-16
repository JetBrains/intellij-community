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
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.util.ConcurrencyUtil
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import com.intellij.util.indexing.IndexInfrastructure
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal class ProjectIndexableFilesFilterHealthCheck(private val project: Project, private val filter: ProjectIndexableFilesFilter) {
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

  private fun runHealthCheck() {
    if (!IndexInfrastructure.hasIndices()) return

    try {
      var errors: List<HealthCheckError> = emptyList()
      ProgressIndicatorUtils.runInReadActionWithWriteActionPriority {
        if (DumbService.isDumb(project)) return@runInReadActionWithWriteActionPriority
        errors = doRunHealthCheck()
      }

      if (errors.isEmpty()) return

      for (error in errors) {
        error.fix()
      }

      val errorsToReport = errors
        .filter { it.shouldBeReported }

      if (errors.size > errorsToReport.size) {
        FileBasedIndexImpl.LOG.info("${errors.size - errorsToReport.size} of ${filter.javaClass.simpleName} health check errors were filtered out")
      }

      if (errorsToReport.isEmpty()) return

      val summary = errorsToReport
        .groupBy { it::class.java }
        .entries.joinToString("\n") { (clazz, e) ->
          "${e.size} ${clazz.simpleName} errors. Examples:\n" + e.take(10).joinToString("\n") { error ->
            ReadAction.nonBlocking(Callable { error.presentableText }).executeSynchronously()
          }
        }

      FileBasedIndexImpl.LOG.warn("${filter.javaClass.simpleName} health check found ${errorsToReport.size} errors in project ${project.name}:\n$summary")
    }
    catch (_: ProcessCanceledException) {

    }
    catch (e: Exception) {
      FileBasedIndexImpl.LOG.error(e)
    }
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
    val projectFileIndex = ProjectFileIndex.getInstance(project)

    for ((fileId, indexable) in fileStatuses) {
      ProgressManager.checkCanceled()
      if (shouldBeIndexable[fileId]) {
        if (!indexable) {
          errors.add(MissingFileIdInFilterError(fileId, filter))
        }
        if (checkAllExpectedIndexableFiles) shouldBeIndexable[fileId] = false
      }
      else if (indexable && !shouldBeIndexable[fileId]) {
        val file = PersistentFS.getInstance().findFileById(fileId)
        val excluded = file != null && projectFileIndex.isExcluded(file)
        errors.add(NotIndexableFileIsInFilterError(fileId, excluded, filter))
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

sealed interface HealthCheckError {
  val presentableText: String
  val shouldBeReported: Boolean
  fun fix()
}

internal class MissingFileIdInFilterError(private val fileId: Int,
                                          private val filter: ProjectIndexableFilesFilter) : HealthCheckError {
  override val shouldBeReported: Boolean = true
  override val presentableText: String
    get() = "file name=${PersistentFS.getInstance().findFileById(fileId)?.name} id=$fileId NOT found in filter"

  override fun fix() {
    filter.ensureFileIdPresent(fileId) { true }
  }
}

internal class NotIndexableFileIsInFilterError(private val fileId: Int,
                                               excluded: Boolean,
                                               private val filter: ProjectIndexableFilesFilter) : HealthCheckError {
  override val shouldBeReported: Boolean = !excluded
  override val presentableText: String
    get() = "file name=${
      PersistentFS.getInstance().findFileById(fileId)?.name
    } id=$fileId is found in filter even though it's NOT indexable"

  override fun fix() {
    filter.removeFileId(fileId)
  }
}