// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.impl.ProgressSuspender
import com.intellij.openapi.progress.util.PingProgress
import com.intellij.openapi.project.*
import com.intellij.openapi.project.MergingTaskQueue.SubmissionReceipt
import com.intellij.openapi.project.UnindexedFilesScannerExecutor.Companion.unwrapTask
import com.intellij.openapi.util.NlsContexts.ProgressText
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport

@ApiStatus.Internal
class UnindexedFilesScannerExecutorImpl(project: Project)
  : Disposable,
    MergingQueueGuiExecutor<FilesScanningTask>(project, MergingTaskQueue(), TaskQueueListener(),
                                               IndexingBundle.message("progress.indexing.scanning"),
                                               IndexingBundle.message("progress.indexing.scanning.paused")),
    UnindexedFilesScannerExecutor {
  private val runningDumbTask = AtomicReference<ProgressIndicator>()
  override val taskId = DumbServiceGuiExecutor.IndexingType.SCANNING

  // note that shouldShowProgressIndicator = false in UnindexedFilesScannerExecutor, so there is no suspender for the progress indicator
  private val pauseReason = MutableStateFlow<PersistentList<String>>(persistentListOf())
  override fun getPauseReason(): StateFlow<PersistentList<String>> = pauseReason

  private class TaskQueueListener : ExecutorStateListener {
    override fun beforeFirstTask(): Boolean = true
    override fun afterLastTask(latestReceipt: SubmissionReceipt?) = Unit
  }

  override fun submitTask(task: FilesScanningTask) {
    thisLogger().debug(Throwable("submit task, thread=${Thread.currentThread()}"))

    // Two tasks with limited checks should be just run one after another.
    // A case of a full check followed by a limited change cancelling the first one and making a full check anew results
    // in endless restart of full checks on Windows with empty Maven cache.
    // So only in case the second one is a full check should the first one be cancelled.
    if (task.isFullIndexUpdate()) {
      // we don't want to execute any of the existing tasks - the only task we want to execute will be submitted the few lines below
      cancelAllTasks()
      cancelRunningScannerTaskInDumbQueue()
    }
    if (UnindexedFilesScannerExecutor.shouldScanInSmartMode()) {
      startTaskInSmartMode(task)
    }
    else {
      startTaskInDumbMode(task)
    }
  }

  private fun startTaskInSmartMode(task: FilesScanningTask) {
    taskQueue.addTask(task)
    startBackgroundProcess(onFinish = {})
  }

  private fun startTaskInDumbMode(task: FilesScanningTask) {
    wrapAsDumbTask(task).queue(project)
  }

  @VisibleForTesting
  fun wrapAsDumbTask(task: FilesScanningTask): DumbModeTask {
    return FilesScanningTaskAsDumbModeTaskWrapper(project, task, runningDumbTask)
  }

  override fun runSingleTask(task: MergingTaskQueue.QueuedTask<FilesScanningTask>, activity: StructuredIdeActivity?) {
    LOG.info("Running task: $task")

    try {
      executeScanningTask(task)
    }
    catch (ignored: ProcessCanceledException) {
      LOG.info("Task canceled (PCE): $task")
    }
    catch (unexpected: Throwable) {
      LOG.error("Failed to execute task $task. ${unexpected.message}", unexpected)
    }
    LOG.info("Task finished: $task")
  }

  private fun executeScanningTask(task: MergingTaskQueue.QueuedTask<FilesScanningTask>) {
    val indicator = task.indicator
    indicator.checkCanceled()
    indicator.setIndeterminate(true)
    val unwrapped = unwrapTask(task) as UnindexedFilesScanner

    val progressReporter = IndexingProgressReporter(indicator)
    val shouldShowProgress: StateFlow<Boolean> = if (unwrapped.shouldHideProgressInSmartMode()) {
      project.service<DumbModeWhileScanningTrigger>().isDumbModeForScanningActive()
    }
    else {
      MutableStateFlow(true)
    }

    val taskScope = CoroutineScope(Dispatchers.Default + Job())
    try {
      val pauseReason = UnindexedFilesScannerExecutor.getInstance(project).getPauseReason()
      val taskIndicator = IndexingProgressReporter.CheckPauseOnlyProgressIndicatorImpl(taskScope, pauseReason)
      IndexingProgressReporter.launchIndexingProgressUIReporter(taskScope, project, shouldShowProgress, progressReporter,
                                                                IndexingBundle.message("progress.indexing.scanning"),
                                                                taskIndicator.getPauseReason())

      // runProcess is needed for taskIndicator to be honored in ProgressManager.checkCanceled calls deep inside tasks
      ProgressManager.getInstance().runProcess({ unwrapped.perform(taskIndicator, progressReporter) }, indicator)
    }
    finally {
      taskScope.cancel()
    }
  }

  private fun cancelRunningScannerTaskInDumbQueue() {
    val indicator = runningDumbTask.get()
    if (indicator != null) {
      indicator.cancel()
      val suspender = ProgressSuspender.getSuspender(indicator)
      if (suspender != null && suspender.isSuspended) {
        suspender.resumeProcess()
      }
    }
  }

  override fun cancelAllTasksAndWait() {
    cancelAllTasks() // this also cancels a running task even if they paused by ProgressSuspender
    while (isRunning.value && !project.isDisposed) {
      PingProgress.interactWithEdtProgress()
      LockSupport.parkNanos(50_000_000)
    }
  }

  override fun dispose() {
    taskQueue.disposePendingTasks()
  }

  override fun shouldShowProgressIndicator(): Boolean = false // will be reported asynchronously via IndexingProgressUIReporter

  /**
   * This method does not have "happens before" semantics. It requests GUI suspender to suspend and executes runnable without waiting for
   * all the running tasks to pause.
   */
  override fun suspendScanningAndIndexingThenRun(activityName: @ProgressText String, runnable: Runnable) {
    suspendAndRun(activityName) { // we only need this call to suspend legacy dumb scanning mode
      pauseReason.update { it.add(activityName) }
      try {
        DumbService.getInstance(project).suspendIndexingAndRun(activityName, runnable)
      }
      finally {
        pauseReason.update { it.remove(activityName) }
      }
    }
  }

  override val hasQueuedTasks: Boolean
    get() = taskQueue.isEmpty

  companion object {
    private val LOG = Logger.getInstance(UnindexedFilesScannerExecutor::class.java)
  }
}
