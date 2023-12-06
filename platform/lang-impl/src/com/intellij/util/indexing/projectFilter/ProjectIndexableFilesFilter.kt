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
import java.util.*
import java.util.concurrent.atomic.AtomicReference

internal abstract class ProjectIndexableFilesFilter : IdFilter() {
  private val parallelUpdatesCounter = AtomicVersionedCounter()

  abstract fun ensureFileIdPresent(fileId: Int, add: () -> Boolean): Boolean
  abstract fun removeFileId(fileId: Int)
  abstract fun resetFileIds()

  protected fun <T> runUpdate(action: () -> T): T {
    parallelUpdatesCounter.update(1)
    try {
      return action()
    }
    finally {
      parallelUpdatesCounter.update(-1)
    }
  }

  fun <T> runAndCheckThatNoChangesHappened(action: () -> T): T {
    val (numberOfParallelUpdates, version) = parallelUpdatesCounter.getCounterAndVersion()
    if (numberOfParallelUpdates != 0) throw ProcessCanceledException()
    val res = action()
    val (numberOfParallelUpdates2, version2) = parallelUpdatesCounter.getCounterAndVersion()
    if (numberOfParallelUpdates2 != 0 || version2 != version) {
      throw ProcessCanceledException()
    }
    return res
  }

  abstract fun runHealthCheck(project: Project): List<HealthCheckError>

  protected fun runHealthCheck(project: Project, checkAllExpectedIndexableFiles: Boolean, fileStatuses: Sequence<Pair<Int, Boolean>>): List<HealthCheckError> {
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