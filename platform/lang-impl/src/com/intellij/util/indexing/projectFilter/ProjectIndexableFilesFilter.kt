// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.projectFilter

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.util.indexing.IdFilter
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.atomic.AtomicLong

internal abstract class ProjectIndexableFilesFilterFactory {
  abstract fun create(project: Project, currentVfsCreationTimestamp: Long): ProjectIndexableFilesFilter
}

@Internal
abstract class ProjectIndexableFilesFilter(val checkAllExpectedIndexableFilesDuringHealthcheck: Boolean) : IdFilter() {
  private val parallelUpdatesCounter = AtomicVersionedCounter()

  override fun getFilteringScopeType(): FilterScopeType = FilterScopeType.PROJECT_AND_LIBRARIES

  internal abstract fun ensureFileIdPresent(fileId: Int, add: () -> Boolean): Boolean
  internal abstract fun removeFileId(fileId: Int)
  internal abstract fun resetFileIds()
  internal open fun onProjectClosing(project: Project, vfsCreationStamp: Long) = Unit

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

  internal fun <T> takeIfNoChangesHappened(outerCompute: Computation<T>): Computation<T> {
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

  internal abstract fun getFileStatuses(): Sequence<Pair<Int, Boolean>>
}

internal enum class FilterActionCancellationReason {
  FILTER_IS_UPDATED,
  SCANNING_IS_IN_PROGRESS,
  MAX_ATTEMPTS_REACHED,
}

private class AtomicVersionedCounter {
  /** [ counter:int32 << 32 | version:int32 ] */
  private val counterAndVersion: AtomicLong = AtomicLong(0L)


  fun update(counterUpdate: Int) {
    while (true) {
      val pair: Long = counterAndVersion.get()
      var counter: Long = (pair shr 32)
      var version: Long = (pair and 0xFFFF_FFFF)

      counter += counterUpdate
      if (counter > Integer.MAX_VALUE) {
        counter = 0
      }
      version++
      if (version > Integer.MAX_VALUE) {
        version = 0
      }

      val newPair = (counter shl 32) or version
      if (counterAndVersion.compareAndSet(pair, newPair)) {
        break
      }
    }
  }

  fun getCounterAndVersion(): Pair<Int, Int> {
    val pair: Long = counterAndVersion.get()
    val counter: Long = (pair shr 32)
    val version: Long = (pair and 0xFFFF_FFFF)
    return Pair(counter.toInt(), version.toInt())
  }
}