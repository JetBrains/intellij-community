// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbModeTask
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.atomic.AtomicReference

@Internal
@VisibleForTesting // Should be package-private, but kotlin does not have this visibility. This is not public API
class UnindexedFilesScannerAsDumbModeTaskWrapper(@VisibleForTesting val task: UnindexedFilesScanner,
                                                 private val runningTask: AtomicReference<ProgressIndicator>) : DumbModeTask() {

  companion object {
    private val LOG = logger<UnindexedFilesScannerExecutor>()
  }

  override fun performInDumbMode(indicator: ProgressIndicator) {
    try {
      val old = runningTask.getAndSet(indicator)
      LOG.assertTrue(old == null, "Old = $old")
      task.perform(indicator)
    }
    finally {
      val old = runningTask.getAndSet(null)
      LOG.assertTrue(old === indicator, "Old = $old")
    }
  }

  override fun tryMergeWith(taskFromQueue: DumbModeTask): DumbModeTask? {
    if (taskFromQueue is UnindexedFilesScannerAsDumbModeTaskWrapper) {
      val scanningTaskFromQueue = taskFromQueue.task
      val merged = task.tryMergeWith(scanningTaskFromQueue)
      LOG.assertTrue(taskFromQueue.runningTask === runningTask, "Should be the same object: ${runningTask}, ${taskFromQueue.runningTask}")
      return merged?.let { UnindexedFilesScannerAsDumbModeTaskWrapper(it, runningTask) }
    }
    else {
      return super.tryMergeWith(taskFromQueue)
    }
  }

  override fun dispose() {
    Disposer.dispose(task)
    super.dispose()
  }
}