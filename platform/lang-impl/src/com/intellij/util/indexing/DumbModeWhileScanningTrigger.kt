// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.DumbServiceImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.UnindexedFilesScannerExecutor
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class DumbModeWhileScanningSubscriber : StartupActivity.RequiredForSmartMode {
  override fun runActivity(project: Project) {
    project.service<DumbModeWhileScanningTrigger>().subscribe()
  }
}

/**
 * Tracks [PerProjectIndexingQueue] state and starts dumb mode if too many dirty files in the queue.
 * You don't need this service except for statistics, because dumb mode can start for many different reasons, not only because of scanning.
 * Use [com.intellij.openapi.project.DumbService] to schedule tasks in smart mode.
 */
@Internal
@Service(Service.Level.PROJECT)
class DumbModeWhileScanningTrigger(private val project: Project, private val cs: CoroutineScope) {
  private val dumbModeForScanningIsActive = MutableStateFlow(false)

  fun isDumbModeForScanningActive(): StateFlow<Boolean> = dumbModeForScanningIsActive

  fun subscribe() {
    if (DumbServiceImpl.isSynchronousTaskExecution) {
      // in synchronous mode it will be a deadlock
      return
    }

    val manyFilesChanged = project.service<PerProjectIndexingQueue>()
      .estimatedFilesCount()
      .map { it >= DUMB_MODE_THRESHOLD }

    subscribe(manyFilesChanged, UnindexedFilesScannerExecutor.getInstance(project).isRunning)
  }

  private fun subscribe(manyFilesChanged: Flow<Boolean>, scanningInProgress: Flow<Boolean>) {
    cs.launch {
      while (true) {
        manyFilesChanged.first { it }
        dumbModeForScanningIsActive.value = true
        try {
          DumbService.getInstance(project).runInDumbMode("Waiting for scanning to complete") {
            // this is kind of trigger with memory: to start dumb mode it's enough to have many changed files, but to end dumb mode
            // we also should wait for all the scanning tasks to finish.
            manyFilesChanged
              // also wait for all the other scanning tasks to complete before starting indexing tasks
              .combine(scanningInProgress) { manyFiles, scanning ->
                !manyFiles && !scanning
              }
              .first { it }
          }
        }
        finally {
          dumbModeForScanningIsActive.value = false
        }
      }
    }
  }

  companion object {
    private val DUMB_MODE_THRESHOLD: Int by lazy { Registry.intValue("scanning.dumb.mode.threshold", 20) }
  }
}