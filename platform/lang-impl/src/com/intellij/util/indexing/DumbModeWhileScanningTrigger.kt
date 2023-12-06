// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.DumbModeTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.UnindexedFilesScannerExecutor
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.registry.Registry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.CountDownLatch

class DumbModeWhileScanningSubscriber : StartupActivity.RequiredForSmartMode {
  override fun runActivity(project: Project) {
    // don't start DumbModeWhileScanning when scanning in smart mode is disabled. Otherwise, due to merged and cancelled tasks,
    // scanning task may appear later in the queue than DumbModeWhileScanning, which effectively means a deadlock
    // (DumbModeWhileScanning waits for a latch that will be counted down in scanning task via PerProjectIndexingQueue.flush)
    val shouldScanInSmartMode = UnindexedFilesScannerExecutor.shouldScanInSmartMode()
    if (shouldScanInSmartMode) {
      project.service<DumbModeWhileScanningTrigger>().subscribe()
    }
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
        val latch = CountDownLatch(1)
        try {
          val dumbTaskFinished = MutableStateFlow(false)
          DumbModeWhileScanning(latch, dumbTaskFinished).queue(project)

          // this is kind of trigger with memory: to start dumb mode it's enough to have many changed files, but to end dumb mode
          // we also should wait for all the scanning tasks to finish.
          manyFilesChanged
            // also wait for all the other scanning tasks to complete before starting indexing tasks
            .combine(scanningInProgress) { manyFiles, scanning ->
              !manyFiles && !scanning
            }
            // quit waiting if task has already finished (e.g. canceled)
            .combine(dumbTaskFinished) { shouldFinishDumbMode, taskFinished ->
              shouldFinishDumbMode || taskFinished
            }
            .first { it }
        }
        finally {
          latch.countDown()
          dumbModeForScanningIsActive.value = false
        }
      }
    }
  }

  private open class DumbModeWhileScanning(private val latch: CountDownLatch,
                                           private val dumbTaskFinished: MutableStateFlow<Boolean>) : DumbModeTask() {
    override fun performInDumbMode(indicator: ProgressIndicator) {
      indicator.isIndeterminate = true
      indicator.text = IndexingBundle.message("progress.indexing.waiting.for.scanning.to.complete")

      ProgressIndicatorUtils.awaitWithCheckCanceled(latch)
    }

    override fun dispose() {
      // This task is not running anymore. For example, it can be cancelled (e.g. by DumbService.cancelAllTasksAndWait())
      // Let the outer DumbModeWhileScanningTrigger know about that
      dumbTaskFinished.compareAndSet(false, true)
    }
  }

  companion object {
    private val DUMB_MODE_THRESHOLD: Int by lazy { Registry.intValue("scanning.dumb.mode.threshold", 20) }
  }
}