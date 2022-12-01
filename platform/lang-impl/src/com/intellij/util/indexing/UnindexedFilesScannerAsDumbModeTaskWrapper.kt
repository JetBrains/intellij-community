// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbModeTask
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus.Internal

// Should be package-private, but kotlin does not have this visibility. This is not public API
@Internal
open class UnindexedFilesScannerAsDumbModeTaskWrapper(val task: UnindexedFilesScanner) : DumbModeTask() {

  override fun performInDumbMode(indicator: ProgressIndicator) {
    task.perform(indicator)
  }

  override fun tryMergeWith(taskFromQueue: DumbModeTask): DumbModeTask? {
    if (taskFromQueue is UnindexedFilesScannerAsDumbModeTaskWrapper) {
      val scanningTaskFromQueue = taskFromQueue.task
      val merged = task.tryMergeWith(scanningTaskFromQueue)
      return merged?.let { UnindexedFilesScannerAsDumbModeTaskWrapper(it) }
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