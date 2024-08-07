// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.progress.util.PingProgress
import com.intellij.openapi.project.*
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.NlsContexts.ProgressText
import com.intellij.openapi.util.registry.Registry
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.concurrency.ThreadingAssertions
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
class UnindexedFilesScannerExecutorImpl(private val project: Project, private val scope: CoroutineScope) : Disposable,
                                                                                                           UnindexedFilesScannerExecutor {
  // helpers for tests
  private val scanningWaitsForNonDumbModeOverride = MutableStateFlow<Boolean?>(null)
  private var taskFilter: Predicate<UnindexedFilesScanner>? = null

  @Volatile
  private var schedulingTasksScope: CoroutineScope = createSchedulingTasksScope()

  // note that shouldShowProgressIndicator = false in UnindexedFilesScannerExecutor, so there is no suspender for the progress indicator
  private val pauseReason = MutableStateFlow<PersistentList<String>>(persistentListOf())
  override fun getPauseReason(): StateFlow<PersistentList<String>> = pauseReason

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

  @Volatile
  private var runningTask: Job? = null

  init {
    // Note about Dispatchers.IO: we'll do "runBlocking" in UnindexedFilesScanner.ScanningSession.collectIndexableFilesConcurrently
    // Make sure that we are not using limited dispatchers here (e.g., Dispatchers.Default).
    scope.childScope("Scanning (root)", Dispatchers.IO).launch {
      suspendIfShouldStartSuspended()
      while (true) {
        try {
          waitUntilNextTaskExecutionAllowed()

          // first set isRunning, otherwise we can find ourselves in a situation
          // isRunning=false, hasScheduledTask=false, but in fact we do have a scheduled task
          // which is about to be running.

          // we don't need a write action here to ensure that 'is in smart mode' doesn't change during read action
          // because scheduling of scanning tasks happens in write action and smart RA waits for scheduled scannings
          isRunning.value = true

          startedOrStoppedEvent.getAndUpdate(Int::inc)

          val task = scanningTask.getAndUpdate { null } ?: continue
          try {
            logInfo("Running task: $task")
            LOG.assertTrue(runningTask == null, "Task is already running (will be cancelled)")
            runningTask?.cancel() // We expect that running task is null. But it's better to be on the safe side
            coroutineScope {
              runningTask = async(CoroutineName("Scanning")) {
                try {
                  val history = runScanningTask(task.task)
                  task.futureHistory.set(history)
                }
                catch (t: Throwable) {
                  task.futureHistory.setException(t)
                  throw t
                } finally {
                  // Scanning may throw exception (or error).
                  // In this case, we should either clear or flush the indexing queue; otherwise, dumb mode will not end in the project.
                  val indexingScheduled = project.service<PerProjectIndexingQueue>().flushNow(task.task.indexingReason)
                  if (!indexingScheduled) {
                    modCount.incrementAndGet()
                  }
                }
              }
            }
            logInfo("Task finished: $task")
          }
          catch (t: Throwable) {
            logInfo("Task interrupted: $task. ${t.message}")
            project.service<ProjectIndexingDependenciesService>().requestHeavyScanningOnProjectOpen("Task interrupted: $task")
            checkCanceled() // this will re-throw cancellation

            // other exceptions: log and forget
            logError("Failed to execute task $task", t)
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
          logError("Unexpected exception during scanning (ignored)", t)
        }
        finally {
          // We don't care about finishing scanning without a write action. This looks harmless at the moment
          isRunning.value = false
          startedOrStoppedEvent.getAndUpdate(Int::inc)
        }
      }
    }
  }

  private fun createSchedulingTasksScope(): CoroutineScope = scope.childScope("UnindexedFilesScannerExecutor scheduling tasks scope")

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

  private suspend fun waitUntilNextTaskExecutionAllowed() {
    // wait until scanning is enabled
    var flow: Flow<Boolean> = scanningEnabled.combine(scanningTask) { enabled, scanningTask ->
      enabled && scanningTask != null
    }

    // Delay scanning tasks until all the scheduled dumb tasks are finished.
    // For example, PythonLanguageLevelPusher.initExtra is invoked from RequiredForSmartModeActivity and may submit additional dumb tasks.
    // We want scanning to start after all these "extra" dumb tasks are finished.
    // Note that a project may become dumb immediately after the check. This is not a problem - we schedule scanning anyway.
    if (scanningWaitsForNonDumbMode()) {
      flow = flow.combine(
        // nested flow is needed because of negation (!shouldWaitForNonDumb)
        DumbServiceImpl.getInstance(project).isDumbAsFlow.combine(scanningWaitsForNonDumbModeOverride) { isDumb, scanningWaitsCurrentValue ->
          isDumb && scanningWaitsForNonDumbMode(scanningWaitsCurrentValue)
        }
      ) { shouldRun, shouldWaitForNonDumb ->
        shouldRun && !shouldWaitForNonDumb
      }
    }

    flow.first { it }
  }

  @TestOnly
  fun overrideScanningWaitsForNonDumbMode(newValue: Boolean?) {
    scanningWaitsForNonDumbModeOverride.value = newValue
  }

  private suspend fun runScanningTask(task: UnindexedFilesScanner): ProjectScanningHistoryImpl {
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

      val scanningHistory = ProjectScanningHistoryImpl(project, task.indexingReason, task.scanningType)
      (GistManager.getInstance() as GistManagerImpl).mergeDependentCacheInvalidations().use {
        task.applyDelayedPushOperations(scanningHistory)
      }
      blockingContext {
        task.perform(taskIndicator, progressReporter, scanningHistory)
      }

      progressScope.cancel()
      return@coroutineScope scanningHistory
    }
  }

  private fun cancelAllTasks(debugReason: String) {
    scanningEnabled.value = false
    try {
      scanningTask.getAndUpdate { null }?.close()
      runningTask?.cancel(debugReason)
    }
    finally {
      scanningEnabled.value = true
    }
  }

  override fun submitTask(task: FilesScanningTask): Future<ProjectScanningHistory> {
    task as UnindexedFilesScanner
    LOG.debug(Throwable("submit task, ${project.name}[${project.locationHash}], thread=${Thread.currentThread()}"))

    var modality = ModalityState.defaultModalityState()
    if (modality == ModalityState.any()) {
      LOG.error("Unexpected modality: should not be ANY. Replace with NON_MODAL")
      modality = ModalityState.nonModal()
    }

    val historyFuture = SettableFuture.create<ProjectScanningHistory>()
    if (ApplicationManager.getApplication().isDispatchThread) {
      ApplicationManager.getApplication().runWriteAction {
        submitTaskOnEdt(task, historyFuture)
      }
    }
    else {
      schedulingTasksScope.launch(modality.asContextElement() + Dispatchers.EDT, start = CoroutineStart.ATOMIC) {
        try {
          ensureActive()
          blockingContext {
            ApplicationManager.getApplication().runWriteAction {
              submitTaskOnEdt(task, historyFuture)
            }
          }
        }
        catch (_: CancellationException) {
          task.close()
        }
      }
    }
    return historyFuture
  }

  private fun submitTaskOnEdt(task: UnindexedFilesScanner, historyFuture: SettableFuture<ProjectScanningHistory>) {
    ThreadingAssertions.assertEventDispatchThread() // make sure that it's not executed in parallel with cancelAllTasksAndWait
    ThreadingAssertions.assertWriteAccess() // state of 'smart mode' should not change in RA

    if (taskFilter?.test(task) == false) {
      logInfo("Skipping task (rejected by filter): $task")
      task.close()
      historyFuture.setException(RejectedExecutionException("(rejected by filter)"))
      return
    }

    // Two tasks with limited checks should be just run one after another.
    // A case of a full check followed by a limited change cancelling the first one and making a full check anew results
    // in endless restart of full checks on Windows with empty Maven cache.
    // So only in case the second one is a full check should the first one be cancelled.
    if (task.isFullIndexUpdate()) { // we don't want to execute any of the existing tasks - the only task we want to execute will be submitted the few lines below
      cancelAllTasks("Full scanning is queued")
    }

    startTaskInSmartMode(task, historyFuture)
  }

  private fun startTaskInSmartMode(task: UnindexedFilesScanner, historyFuture: SettableFuture<ProjectScanningHistory>) {
    lateinit var new: ScheduledScanningTask
    do {
      val old = scanningTask.value
      new = if (old != null) {
        ScheduledScanningTask(old.task.tryMergeWith(task), old.futureHistory)
      }
      else {
        ScheduledScanningTask(task, historyFuture)
      }

      val updated = scanningTask.compareAndSet(old, new)
      if (updated) {
        if (old != null) {
          old.close()
          historyFuture.listenTo(old.futureHistory)
        }
        if (new.task != task) {
          task.close()
        }
      }
      else {
        new.close()
      }
    }
    while (!updated)
  }

  override fun cancelAllTasksAndWait() {
    ThreadingAssertions.assertEventDispatchThread()

    cancelAllTasks("cancelAllTasksAndWait") // this also cancels a running task even if it is paused by ProgressSuspender
    while (isRunning.value && !project.isDisposed) {
      PingProgress.interactWithEdtProgress()
      LockSupport.parkNanos(50_000_000)
    }

    val oldTaskScope = schedulingTasksScope
    schedulingTasksScope = createSchedulingTasksScope()
    // schedulingTasksScope cannot be running because is uses EDT, but we should cancel scheduled and not launched yet jobs
    oldTaskScope.cancel("UnindexedFilesScannerExecutor.cancelAllTasksAndWait", ProcessCanceledException())
  }

  override fun dispose() {
    scanningTask.getAndUpdate { null }?.close()
    runningTask?.cancel()
  }

  /**
   * This method does not have "happens before" semantics. It requests GUI suspender to suspend and executes runnable without waiting for
   * all the running tasks to pause.
   */
  override fun suspendScanningAndIndexingThenRun(activityName: @ProgressText String, runnable: Runnable) {
    pauseReason.update { it.add(activityName) }
    try {
      DumbService.getInstance(project).suspendIndexingAndRun(activityName, runnable)
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

private fun <T> SettableFuture<T>.listenTo(future: SettableFuture<T>) {
  val listeningFuture = this

  Futures.addCallback(future, object : FutureCallback<T> {
    override fun onSuccess(result: T?) {
      listeningFuture.set(result)
    }

    override fun onFailure(t: Throwable) {
      listeningFuture.setException(t)
    }

  }, MoreExecutors.directExecutor())
}
