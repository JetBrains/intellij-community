// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing.projectFilter

import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.util.containers.ConcurrentBitSet
import com.intellij.util.indexing.IdFilter
import java.util.concurrent.atomic.AtomicReference

internal class IncrementalProjectIndexableFilesFilter : IdFilter() {
  @Volatile
  private var fileIds: ConcurrentBitSet = ConcurrentBitSet.create()
  private var previousFileIds: ConcurrentBitSet? = null
  private val parallelUpdatesCounter = AtomicVersionedCounter()

  override fun getFilteringScopeType(): FilterScopeType = FilterScopeType.PROJECT_AND_LIBRARIES

  override fun containsFileId(fileId: Int): Boolean = fileIds.get(fileId)

  @Suppress("LocalVariableName")
  fun ensureFileIdPresent(fileId: Int, add: () -> Boolean): FileAddStatus {
    assert(fileId > 0)

    return runUpdate {
      val _fileIds = fileIds
      if (_fileIds.get(fileId)) {
        FileAddStatus.PRESENT
      }
      else if (add()) {
        _fileIds.set(fileId)
        val _previousFileIds = previousFileIds
        if (_previousFileIds == null || !_previousFileIds.get(fileId)) FileAddStatus.ADDED else FileAddStatus.PRESENT
      }
      else FileAddStatus.SKIPPED
    }
  }

  fun removeFileId(fileId: Int) {
    assert(fileId > 0)
    runUpdate {
      fileIds.clear(fileId)
    }
  }

  fun memoizeAndResetFileIds() {
    // called in sequential UnindexedFileUpdater tasks
    runUpdate {
      previousFileIds = fileIds
      fileIds = ConcurrentBitSet.create()
    }
  }

  fun resetPreviousFileIds() {
    // called in sequential UnindexedFileUpdater tasks
    runUpdate {
      previousFileIds = null
    }
  }

  private fun <T> runUpdate(action: () -> T): T {
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
