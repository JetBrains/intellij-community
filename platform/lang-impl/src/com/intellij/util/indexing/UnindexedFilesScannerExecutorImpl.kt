// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.progress.util.PingProgress
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.FilesScanningTask
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.UnindexedFilesScannerExecutor
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.NlsContexts.ProgressText
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.application
import com.intellij.util.gist.GistManager
import com.intellij.util.gist.GistManagerImpl
import com.intellij.util.indexing.dependencies.ProjectIndexingDependenciesService
import com.intellij.util.indexing.diagnostic.ProjectScanningHistory
import com.intellij.util.indexing.diagnostic.ProjectScanningHistoryImpl
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport
import java.util.function.Predicate

@ApiStatus.Internal
class UnindexedFilesScannerExecutorImpl(private val project: Project, cs: CoroutineScope) : Disposable,
                                                                                            UnindexedFilesScannerExecutor {
  // helpers for tests
  private val scanningWaitsForNonDumbModeOverride = MutableStateFlow<Boolean?>(null)
  private var taskFilter: Predicate<UnindexedFilesScanner>? = null

  // note that shouldShowProgressIndicator = false in UnindexedFilesScannerExecutor, so there is no suspender for the progress indicator
  private val pauseReason = MutableStateFlow<PersistentList<String>>(persistentListOf())
  override fun getPauseReason(): StateFlow<PersistentList<String>> = pauseReason

  // 1. Should only be SET inside WA to prevent mode change during RA.
  // 2. Will be cleared without WA to avoid deadlocks when some code waits for smart mode under modal progress
  //    (at the moment there are no real arguments for WA to clear the flag)
  // 3. You may set isRunning = true anywhere in the code (given, that it is set under WA), but never set to false.
  //    Only executor coroutine may set it to false, otherwise isRunning will be cleared in the middle of scanning task execution.
  override val isRunning: MutableStateFlow<Boolean> = MutableStateFlow(false)

  /**
   * Modification counter that increases each time the executor starts or stops
   *
   * This is not the same as [isRunning], because [isRunning] is a state flow, meaning that it is conflated and deduplicated, i.e. short
   * transitions true-false-true can be missed in [isRunning]. [startedOrStoppedEvent] is still conflated, but never miss the latest event.
   *
   * TODO: [isRunning] should be a shared flow without deduplication, then we wont need [startedOrStoppedEvent]
   */
  override val startedOrStoppedEvent: MutableStateFlow<Int> = MutableStateFlow(0)

  private val modCount = AtomicLong()
  override val modificationTracker: ModificationTracker = object : ModificationTracker {
    override fun getModificationCount(): Long = modCount.get()
  }

  private class ScheduledScanningTask(val task: UnindexedFilesScanner, val futureHistory: SettableFuture<ProjectScanningHistory>) {
    fun close() = task.close()
  }

  private val scanningTask = MutableStateFlow<ScheduledScanningTask?>(null)
  private val scanningEnabled = MutableStateFlow(true)

  private val mergeScanningParametersScope = cs.childScope("Scanning (merge parameters)")

  @Volatile
  private var runningTask: Job? = null

  init {
    // Note about Dispatchers.IO: we'll do "runBlocking" in UnindexedFilesScanner.ScanningSession.collectIndexableFilesConcurrently
    // Make sure that we are not using limited dispatchers here (e.g., Dispatchers.Default).
    cs.childScope("Scanning (root)", Dispatchers.IO).launch {
      suspendIfShouldStartSuspended()

      val nextTaskExecutionAllowed: Flow<Boolean> = nextTaskExecutionAllowed()

      async(CoroutineName("scanning task execution trigger")) {
        while (true) {
          isRunning.combine(nextTaskExecutionAllowed) { running, allowed -> !running && allowed }.first { it }
          // write action is needed, because otherwise we may get "Constraint inSmartMode cannot be satisfied" in NBRA
          edtWriteAction {
            // we should only set the flag here (if needed), not clear it,
            // otherwise, isRunning may become false in the middle of scanning task execution
            isRunning.value = isRunning.value || scanningTask.value != null
          }
        }
      }

      val scanningIndexingMutex = project.service<PerProjectIndexingQueue>().scanningIndexingMutex
      val mutexOwner = "scanning"

      while (true) {
        var mutexAcquired = false
        try {
          // first wait for isRunning, otherwise we can find ourselves in a situation
          // isRunning=false, hasScheduledTask=false, but in fact we do have a scheduled task
          // which is about to be running.
          isRunning.first { it == true }

          scanningIndexingMutex.lock(mutexOwner)
          mutexAcquired = true

          if (!nextTaskExecutionAllowed.first()) {
            continue // to finally block which will clear isRunning flag and release scanningIndexingMutex
            // There are no situations where we need isRunning to be cleared, neither we have situations where we need isRunning stay intact.
            // Feel free to adjust this logic as needed. Clearing the flag looks like the "least surprising" behavior to me.
          }

          startedOrStoppedEvent.getAndUpdate(Int::inc)

          val task = scanningTask.getAndUpdate { null } ?: continue
          try {
            logInfo("Running task: $task")
            LOG.assertTrue(runningTask == null, "Task is already running (will be cancelled)")
            runningTask?.cancel() // We expect that running task is null. But it's better to be on the safe side
            val scanningParameters = task.task.getScanningParameters()
            if (scanningParameters is ScanningIterators) {
              val history = supervisorScope {
                runningTask = coroutineContext.job
                withContext(CoroutineName("Scanning")) {
                  try {
                    runScanningTask(task.task, scanningParameters)
                  }
                  finally {
                    // Scanning may throw exception (or error).
                    // In this case, we should either clear or flush the indexing queue; otherwise, dumb mode will not end in the project.
                    // TODO: we should flush the queue before setting the future, otherwise we have a race in UnindexedFilesScannerTest:
                    //  it clears "allowFlushing" after future is set, expecting that if flush might be called, it had already been called
                    val indexingScheduled = project.service<PerProjectIndexingQueue>().flushNow(scanningParameters.indexingReason)
                    if (!indexingScheduled) {
                      modCount.incrementAndGet()
                    }
                  }
                }
              }
              task.futureHistory.set(history)
              logInfo("Task finished (scanning id=${history.scanningSessionId}): $task")
            }
            else {
              logInfo("Skipping task: $task")
            }
          }
          catch (t: Throwable) {
            task.futureHistory.setException(t)
            logInfo("Task interrupted: $task. ${t.message}")
            project.service<ProjectIndexingDependenciesService>().requestHeavyScanningOnProjectOpen("Task interrupted: $task")
            checkCanceled() // this will re-throw cancellation

            // other exceptions: log and forget
            if (t is ControlFlowException || t is CancellationException) {
              LOG.infoWithDebug(prepareLogMessage("Task was cancelled: $task. " +
                                                  "(enable debug log to see cancellation trace)"), RuntimeException(t))
            }
            else {
              logError("Failed to execute task $task", t)
            }
          }
          finally {
            task.close()
            LOG.assertTrue(runningTask?.isActive != true, "Task job should have been cancelled or finished")
            runningTask = null
          }
        }
        catch (t: Throwable) {
          checkCanceled() // this will re-throw cancellation

          // other exceptions: log and forget
          try {
            logError("Unexpected exception during scanning (ignored)", t)
          }
          catch (_: Throwable) {
            // If logError throws, we ignore this exception, because this will stop scanning service for the project.
            // NOTE: logError throws AE in tests.
          }
        }
        finally {
          // There is no race. When a task is submitted, the reference to scanningTask is updated first (hasQueuedTasks == true), then
          // optionally, isRunning set to true. There is no chance clear isRunning flag by accident.
          //
          // We don't use WA. This allows scanning finishing during RA or while modal dialog is shown
          // (feel free to add WA if you know why finishing is not desired)
          isRunning.value = hasQueuedTasks
          startedOrStoppedEvent.getAndUpdate(Int::inc)
          if (mutexAcquired) {
            scanningIndexingMutex.unlock(mutexOwner)
            mutexAcquired = false
          }
        }
      }
    }
  }

  private suspend fun suspendIfShouldStartSuspended() {
    if (IndexInfrastructure.isIndexesInitializationSuspended()) {
      supervisorScope {
        async {
          withBackgroundProgress(project, IndexingBundle.message("progress.indexing.started.as.suspended")) {
            while (true) {
              delay(1000) // wait for cancellation
            }
          }
        }
      }
    }
  }

  private fun prepareLogMessage(message: String) = "[${project.locationHash}] $message"
  private fun logInfo(message: String) = LOG.info(prepareLogMessage(message))
  private fun logError(message: String, t: Throwable) = LOG.error(prepareLogMessage(message), t)

  private fun scanningWaitsForNonDumbMode(override: Boolean?): Boolean = override ?: Registry.`is`("scanning.waits.for.non.dumb.mode", true)

  @VisibleForTesting
  fun scanningWaitsForNonDumbMode(): Boolean = scanningWaitsForNonDumbMode(scanningWaitsForNonDumbModeOverride.value)

  private fun nextTaskExecutionAllowed(): Flow<Boolean> {
    data class ExecutorState(val enabled: Boolean, val isRunning: Boolean, val hasTask: Boolean, val isDumb: Boolean, val shouldWaitForNonDumb: Boolean)

    var flow: Flow<ExecutorState> = scanningEnabled
      .combine(scanningTask) { enabled, task -> ExecutorState(enabled, false, task != null, false, false) }
      .combine(isRunning) { state, running -> state.copy(isRunning = running) }

    // Delay scanning tasks until all the scheduled dumb tasks are finished.
    // For example, PythonLanguageLevelPusher.initExtra is invoked from RequiredForSmartModeActivity and may submit additional dumb tasks.
    // We want scanning to start after all these "extra" dumb tasks are finished.
    // Note that a project may become dumb immediately after the check. This is not a problem - we schedule scanning anyway.
    if (scanningWaitsForNonDumbMode()) {
      flow = flow.combine(DumbService.getInstance(project).state) { state, dumbState ->
        state.copy(isDumb = dumbState.isDumb)
      }.combine(scanningWaitsForNonDumbModeOverride) { state, scanningWaitsOverride ->
        state.copy(shouldWaitForNonDumb = scanningWaitsForNonDumbMode(scanningWaitsOverride))
      }
    }

    return flow.map { it.enabled && it.hasTask &&
                      // Warning: don't wait for smart mode if scanning is already running
                      (it.isRunning || !(it.isDumb && it.shouldWaitForNonDumb)) }
  }

  @TestOnly
  fun overrideScanningWaitsForNonDumbMode(newValue: Boolean?) {
    scanningWaitsForNonDumbModeOverride.value = newValue
  }

  private suspend fun runScanningTask(task: UnindexedFilesScanner, scanningParameters: ScanningIterators): ProjectScanningHistoryImpl {
    val shouldShowProgress: StateFlow<Boolean> = if (task.shouldHideProgressInSmartMode()) {
      project.service<DumbModeWhileScanningTrigger>().isDumbModeForScanningActive()
    }
    else {
      MutableStateFlow(true)
    }
    return coroutineScope{
      val progressScope = childScope("Scanning progress")
      val progressReporter = IndexingProgressReporter()
      val taskIndicator = IndexingProgressReporter.CheckPauseOnlyProgressIndicatorImpl(progressScope, getPauseReason())
      IndexingProgressReporter.launchIndexingProgressUIReporter(progressScope, project, shouldShowProgress, progressReporter,
                                                                IndexingBundle.message("progress.indexing.scanning"),
                                                                taskIndicator.getPauseReason())

      val scanningHistory = ProjectScanningHistoryImpl(project, scanningParameters.indexingReason, scanningParameters.scanningType)
      (GistManager.getInstance() as GistManagerImpl).mergeDependentCacheInvalidations().use {
        task.applyDelayedPushOperations(scanningHistory)
      }
      task.perform(taskIndicator, progressReporter, scanningHistory, scanningParameters)

      progressScope.cancel()
      return@coroutineScope scanningHistory
    }
  }

  private fun cancelAllTasks(debugReason: String) {
    val wasEnabled = scanningEnabled.value
    if (wasEnabled) scanningEnabled.value = false
    try {
      scanningTask.getAndUpdate { null }?.close()
      runningTask?.cancel(debugReason)
    }
    finally {
      if (wasEnabled) scanningEnabled.value = true
    }
  }

  override fun submitTask(task: FilesScanningTask): Future<ProjectScanningHistory> {
    task as UnindexedFilesScanner
    LOG.debug(Throwable("submit task, ${project.name}[${project.locationHash}], thread=${Thread.currentThread()}"))

    if (taskFilter?.test(task) == false) {
      logInfo("Skipping task (rejected by filter): $task")
      task.close()
      return SettableFuture.create<ProjectScanningHistory>().also { settableFuture ->
        settableFuture.setException(RejectedExecutionException("(rejected by filter)"))
      }
    }

    // Two tasks with limited checks should be just run one after another.
    // A case of a full check followed by a limited change cancelling the first one and making a full check anew results
    // in endless restart of full checks on Windows with empty Maven cache.
    // So only in case the second one is a full check should the first one be cancelled.
    val isFullIndexUpdate = task.isFullIndexUpdate()
    if (isFullIndexUpdate != null && isFullIndexUpdate) {
      // we don't want to execute any of the existing tasks - the only task we want to execute will be submitted the few lines below
      cancelAllTasks("Full scanning is queued")
    }

    val res = startTaskInSmartMode(task)

    if (application.isWriteIntentLockAcquired) {
      // make this executor "running" immediately: clients immediately invoking "runWhenSmart" expect that this scanning is processed first.
      application.runWriteAction {
        if (hasQueuedTasks) {
          isRunning.value = true
        } // else: the task is already picked by the executor. Don't touch isRunning in this case.
        // There is no problem if the task is not only picked by the executor, but also completed, and isRunning is
        // already set to false - there will be one "empty" cycle performed by the executor, and nothing bad.
      }
    }

    return res
  }

  private fun startTaskInSmartMode(task: UnindexedFilesScanner): Future<ProjectScanningHistory> {
    lateinit var new: ScheduledScanningTask
    do {
      val old = scanningTask.value
      new = if (old != null) {
        ScheduledScanningTask(old.task.tryMergeWith(task, mergeScanningParametersScope), old.futureHistory)
      }
      else {
        ScheduledScanningTask(task, SettableFuture.create())
      }

      val updated = scanningTask.compareAndSet(old, new)
      if (updated) {
        old?.close()
        if (new.task != task) {
          task.close()
        }
      }
      else {
        new.close()
      }
    }
    while (!updated)
    return new.futureHistory
  }

  override fun cancelAllTasksAndWait() {
    cancelAllTasks("cancelAllTasksAndWait") // this also cancels a running task even if it is paused by ProgressSuspender
    // we don't check isRunning here, because this method is usually invoked on EDT. There is no chance for a bgt thread to clear isRunning flag.
    while (runningTask?.isActive == true && !project.isDisposed) {
      PingProgress.interactWithEdtProgress()
      LockSupport.parkNanos(50_000_000)
    }
  }

  override fun dispose() {
    mergeScanningParametersScope.cancel()
    scanningTask.getAndUpdate { null }?.close()
    runningTask?.cancel()
  }

  /**
   * This method does not have "happens before" semantics. It requests GUI suspender to suspend and executes runnable without waiting for
   * all the running tasks to pause.
   */
  @Suppress("OVERRIDE_DEPRECATION")
  override fun suspendScanningAndIndexingThenRun(activityName: @ProgressText String, runnable: Runnable) {
    pauseReason.update { it.add(activityName) }
    try {
      DumbService.getInstance(project).suspendIndexingAndRun(activityName, runnable)
    }
    finally {
      pauseReason.update { it.remove(activityName) }
    }
  }

  override suspend fun suspendScanningAndIndexingThenExecute(
    activityName: @ProgressText String,
    activity: suspend CoroutineScope.() -> Unit,
  ) {
    pauseReason.update { it.add(activityName) }
    try {
      project.serviceAsync<DumbService>().suspendIndexingAndRun(activityName, activity)
    }
    finally {
      pauseReason.update { it.remove(activityName) }
    }
  }

  override fun suspendQueue() {
    scanningEnabled.value = false
  }

  override fun resumeQueue() {
    scanningEnabled.value = true
  }

  override val hasQueuedTasks: Boolean
    get() = scanningTask.value != null

  @TestOnly
  fun setTaskFilterInTest(disposable: Disposable, filter: Predicate<UnindexedFilesScanner>) {
    Disposer.register(disposable) { taskFilter = null }
    taskFilter = filter
  }

  companion object {
    private val LOG = Logger.getInstance(UnindexedFilesScannerExecutor::class.java)

    @JvmStatic
    fun getInstance(project: Project): UnindexedFilesScannerExecutorImpl = UnindexedFilesScannerExecutor.getInstance(project) as UnindexedFilesScannerExecutorImpl
  }
}
