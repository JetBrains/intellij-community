// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.projectFilter

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexImpl
import com.intellij.util.indexing.IdFilter
import com.intellij.util.indexing.dependencies.ProjectIndexingDependenciesService
import java.util.*
import java.util.concurrent.atomic.AtomicReference

internal interface ProjectIndexableFilesFilterFactory {
  fun create(project: Project): ProjectIndexableFilesFilter
}

internal abstract class ProjectIndexableFilesFilter(private val checkAllExpectedIndexableFilesDuringHealthcheck: Boolean) : IdFilter() {
  private val parallelUpdatesCounter = AtomicVersionedCounter()

  override fun getFilteringScopeType(): FilterScopeType = FilterScopeType.PROJECT_AND_LIBRARIES

  abstract fun ensureFileIdPresent(fileId: Int, add: () -> Boolean): Boolean
  abstract fun removeFileId(fileId: Int)
  abstract fun resetFileIds()
  open fun onProjectClosing(project: Project) = Unit

  protected fun <T> runUpdate(action: () -> T): T {
    parallelUpdatesCounter.update(1)
    try {
      return action()
    }
    finally {
      parallelUpdatesCounter.update(-1)
    }
  }

  private fun <T> runAndCheckThatNoChangesHappened(action: () -> T): T {
    val (numberOfParallelUpdates, version) = parallelUpdatesCounter.getCounterAndVersion()
    if (numberOfParallelUpdates != 0) throw ProcessCanceledException()
    val res = action()
    val (numberOfParallelUpdates2, version2) = parallelUpdatesCounter.getCounterAndVersion()
    if (numberOfParallelUpdates2 != 0 || version2 != version) {
      throw ProcessCanceledException()
    }
    return res
  }

  fun runHealthCheck(project: Project): List<HealthCheckError> {
    return runIfScanningScanningIsCompleted(project) {
      runAndCheckThatNoChangesHappened {
        // It is possible that scanning will start and finish while we are performing healthcheck,
        // but then healthcheck will be terminated by the fact that filter was update.
        // If it was not updated, then we don't care that scanning happened, and we can trust healthcheck result
        runHealthCheck(project, checkAllExpectedIndexableFilesDuringHealthcheck, getFileStatuses())
      }
    }
  }

  protected abstract fun getFileStatuses(): Sequence<Pair<Int, Boolean>>

  /**
   * This healthcheck makes the most sense for [com.intellij.util.indexing.projectFilter.IncrementalProjectIndexableFilesFilter]
   * because it's filled during scanning which iterates over [com.intellij.util.indexing.roots.IndexableFilesIterator]s
   * so if we iterate over IndexableFilesIterator again, it should match filter.
   * Such errors would mean that we missed some event when a file became (un)indexed e.g., when it was deleted or marked excluded.
   *
   * Errors reported for [com.intellij.util.indexing.projectFilter.CachingProjectIndexableFilesFilter] could also mean an inconsistency
   * between [com.intellij.util.indexing.roots.IndexableFilesIterator] and [com.intellij.util.indexing.IndexableFilesIndex].
   */
  private fun runHealthCheck(project: Project, checkAllExpectedIndexableFiles: Boolean, fileStatuses: Sequence<Pair<Int, Boolean>>): List<HealthCheckError> {
    val errors = mutableListOf<HealthCheckError>()

    val shouldBeIndexable = getFilesThatShouldBeIndexable(project)

    for ((fileId, indexable) in fileStatuses) {
      ProgressManager.checkCanceled()
      if (shouldBeIndexable[fileId]) {
        if (!indexable) {
          errors.add(MissingFileIdInFilterError(fileId, this))
        }
        if (checkAllExpectedIndexableFiles) shouldBeIndexable[fileId] = false
      }
      else if (indexable && !shouldBeIndexable[fileId]) {
        errors.add(NotIndexableFileIsInFilterError(fileId, this))
      }
    }

    if (checkAllExpectedIndexableFiles) {
      for (fileId in 0 until shouldBeIndexable.size()) {
        if (shouldBeIndexable[fileId]) {
          errors.add(MissingFileIdInFilterError(fileId, this))
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

  interface HealthCheckError {
    val presentableText: String
    fun fix()
  }

  class MissingFileIdInFilterError(private val fileId: Int,
                                   private val filter: ProjectIndexableFilesFilter): HealthCheckError {
    override val presentableText: String
      get() = "file name=${PersistentFS.getInstance().findFileById(fileId)?.name} id=$fileId NOT found in filter"

    override fun fix() {
      filter.ensureFileIdPresent(fileId) { true }
    }
  }

  class NotIndexableFileIsInFilterError(private val fileId: Int,
                                        private val filter: ProjectIndexableFilesFilter) : HealthCheckError {
    override val presentableText: String
      get() = "file name=${
        PersistentFS.getInstance().findFileById(fileId)?.name
      } id=$fileId is found in filter even though it's NOT indexable"

    override fun fix() {
      filter.removeFileId(fileId)
    }
  }
}

private class AtomicVersionedCounter {
  private val counterAndVersion = AtomicReference(0 to 0)

  fun update(counterUpdate: Int) {
    var pair = counterAndVersion.get()
    while (!counterAndVersion.compareAndSet(pair, pair.first + counterUpdate to pair.second + 1)) {
      pair = counterAndVersion.get()
    }
  }

  fun getCounterAndVersion(): Pair<Int, Int> = counterAndVersion.get()
}

private fun <T> runIfScanningScanningIsCompleted(project: Project, action: () -> T): T {
  val service = project.getService(ProjectIndexingDependenciesService::class.java)
  if (!service.isScanningCompleted()) throw ProcessCanceledException()
  val res = action()
  if (!service.isScanningCompleted()) throw ProcessCanceledException()
  return res
}