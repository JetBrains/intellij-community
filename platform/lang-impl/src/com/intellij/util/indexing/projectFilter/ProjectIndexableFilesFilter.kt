// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.projectFilter

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.util.indexing.IdFilter
import java.util.concurrent.atomic.AtomicReference

internal abstract class ProjectIndexableFilesFilterFactory {
  abstract fun create(project: Project): ProjectIndexableFilesFilter
}

internal abstract class ProjectIndexableFilesFilter(val checkAllExpectedIndexableFilesDuringHealthcheck: Boolean) : IdFilter() {
  private val parallelUpdatesCounter = AtomicVersionedCounter()

  override fun getFilteringScopeType(): FilterScopeType = FilterScopeType.PROJECT_AND_LIBRARIES

  abstract fun ensureFileIdPresent(fileId: Int, add: () -> Boolean): Boolean
  abstract fun removeFileId(fileId: Int)
  abstract fun resetFileIds()
  open fun onProjectClosing(project: Project) = Unit

  /**
   * This is a temp method
   */
  open val wasDataLoadedFromDisk: Boolean = false

  protected fun <T> runUpdate(action: () -> T): T {
    parallelUpdatesCounter.update(1)
    try {
      return action()
    }
    finally {
      parallelUpdatesCounter.update(-1)
    }
  }

  fun <T> takeIfNoChangesHappened(outerCompute: Computation<T>): Computation<T> {
    return object : Computation<T> {
      override fun compute(checkCancelled: () -> Unit): T {
        val (_, version) = parallelUpdatesCounter.getCounterAndVersion()
        return outerCompute.compute {
          checkCancelled()
          val (numberOfParallelUpdates2, version2) = parallelUpdatesCounter.getCounterAndVersion()
          if (numberOfParallelUpdates2 != 0 || version2 != version) {
            throw ProcessCanceledException()
          }
        }
      }
    }
  }

  abstract fun getFileStatuses(): Sequence<Pair<Int, Boolean>>
}

internal enum class FilterActionCancellationReason {
  FILTER_IS_UPDATED,
  SCANNING_IS_IN_PROGRESS,
  MAX_ATTEMPTS_REACHED,
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