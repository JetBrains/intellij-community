// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.google.common.util.concurrent.SettableFuture
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.*
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdaterImpl
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.diagnostic.telemetry.Indexes
import com.intellij.platform.diagnostic.telemetry.TelemetryManager.Companion.getInstance
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.gist.GistManager
import com.intellij.util.gist.GistManagerImpl
import com.intellij.util.indexing.FilesFilterScanningHandler.IdleFilesFilterScanningHandler
import com.intellij.util.indexing.FilesFilterScanningHandler.UpdatingFilesFilterScanningHandler
import com.intellij.util.indexing.IndexingProgressReporter.CheckPauseOnlyProgressIndicator
import com.intellij.util.indexing.dependencies.ProjectIndexingDependenciesService
import com.intellij.util.indexing.dependencies.ScanningRequestToken
import com.intellij.util.indexing.dependenciesCache.DependenciesIndexedStatusService
import com.intellij.util.indexing.dependenciesCache.DependenciesIndexedStatusService.StatusMark
import com.intellij.util.indexing.diagnostic.*
import com.intellij.util.indexing.diagnostic.dto.JsonScanningStatistics
import com.intellij.util.indexing.roots.IndexableFileScanner
import com.intellij.util.indexing.roots.IndexableFileScanner.ScanSession
import com.intellij.util.indexing.roots.IndexableFilesDeduplicateFilter
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin
import com.intellij.util.indexing.roots.kind.SdkOrigin
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.RENDEZVOUS
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.io.Closeable
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.locks.LockSupport
import java.util.function.Consumer
import java.util.function.Predicate
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

@ApiStatus.Internal
class UnindexedFilesScanner private constructor(private val myProject: Project,
                                                private val myStartSuspended: Boolean,
                                                private val myOnProjectOpen: Boolean,
                                                isIndexingFilesFilterUpToDate: Boolean,
                                                val predefinedIndexableFilesIterators: List<IndexableFilesIterator>?,
                                                mark: StatusMark?,
                                                private val indexingReason: String,
                                                val scanningType: ScanningType,
                                                private val startCondition: Future<*>?,
                                                private val shouldHideProgressInSmartMode: Boolean?,
                                                private val futureScanningHistory: SettableFuture<ProjectScanningHistory>,
                                                private val forceReindexingTrigger: Predicate<IndexedFile>?) : FilesScanningTask,
                                                                                                               Closeable {
  enum class TestMode {
    PUSHING, PUSHING_AND_SCANNING
  }

  private val myIndex = FileBasedIndex.getInstance() as FileBasedIndexImpl
  private val myFilterHandler: FilesFilterScanningHandler
  private val myProvidedStatusMark: StatusMark?
  private val taskToken = myProject.getService(ProjectIndexingDependenciesService::class.java).newIncompleteTaskToken()
  private var flushQueueAfterScanning = true
  private val scanningHistory = ProjectScanningHistoryImpl(myProject, indexingReason, scanningType)

  @TestOnly
  constructor(project: Project) : this(project, false, false, false, null, null,
                                       "<unknown>", ScanningType.FULL, null)


  constructor(project: Project,
              indexingReason: String) : this(project, false, false, false, null, null, indexingReason, ScanningType.FULL, null)

  constructor(project: Project,
              indexingReason: String,
              shouldHideProgressInSmartMode: Boolean?) : this(project, false, false, false, null, null, indexingReason, ScanningType.FULL,
                                                              null, shouldHideProgressInSmartMode,
                                                              null)

  constructor(project: Project,
              predefinedIndexableFilesIterators: List<IndexableFilesIterator>?,
              mark: StatusMark?,
              indexingReason: String) : this(project, false, false, false, predefinedIndexableFilesIterators, mark, indexingReason,
                                             if (predefinedIndexableFilesIterators == null) ScanningType.FULL else ScanningType.PARTIAL,
                                             null)

  @JvmOverloads
  constructor(project: Project,
              startSuspended: Boolean,
              onProjectOpen: Boolean,
              isIndexingFilesFilterUpToDate: Boolean,
              predefinedIndexableFilesIterators: List<IndexableFilesIterator>?,
              mark: StatusMark?,
              indexingReason: String,
              scanningType: ScanningType,
              startCondition: Future<*>?,
              shouldHideProgressInSmartMode: Boolean? = null,
              forceReindexTrigger: Predicate<IndexedFile>? = null) : this(project, startSuspended, onProjectOpen,
                                                                          isIndexingFilesFilterUpToDate, predefinedIndexableFilesIterators,
                                                                          mark, indexingReason, scanningType, startCondition,
                                                                          shouldHideProgressInSmartMode,
                                                                          SettableFuture.create<ProjectScanningHistory>(),
                                                                          forceReindexTrigger)

  init {
    val filterHolder = myIndex.indexableFilesFilterHolder
    myFilterHandler = if (isIndexingFilesFilterUpToDate) IdleFilesFilterScanningHandler()
    else UpdatingFilesFilterScanningHandler(filterHolder)
    myProvidedStatusMark = if (predefinedIndexableFilesIterators == null) null else mark
    LOG.assertTrue(this.predefinedIndexableFilesIterators == null || !predefinedIndexableFilesIterators.isEmpty())
    LOG.assertTrue( // doing partial scanning of only dirty files on startup
      !myOnProjectOpen ||
      isIndexingFilesFilterUpToDate || this.predefinedIndexableFilesIterators == null,
      "Should request full scanning on project open")
  }

  private fun defaultHideProgressInSmartModeStrategy(): Boolean {
    return Registry.`is`("scanning.hide.progress.in.smart.mode", true) &&
           myProject.getUserData(FIRST_SCANNING_REQUESTED) == FirstScanningState.REQUESTED
  }

  fun shouldHideProgressInSmartMode(): Boolean {
    return shouldHideProgressInSmartMode ?: defaultHideProgressInSmartModeStrategy()
  }

  override fun isFullIndexUpdate(): Boolean {
    return predefinedIndexableFilesIterators == null
  }

  // TODO: Must not change state. We want to allow this method in CAS re-computations
  fun tryMergeWith(oldTask: FilesScanningTask): UnindexedFilesScanner {
    oldTask as UnindexedFilesScanner

    LOG.assertTrue(myProject == oldTask.myProject)
    val reason = if (oldTask.isFullIndexUpdate()) {
      oldTask.indexingReason
    }
    else if (isFullIndexUpdate()) {
      indexingReason
    }
    else {
      "Merged " + indexingReason.removePrefix("Merged ") +
      " with " + oldTask.indexingReason.removePrefix("Merged ")
    }
    LOG.debug("Merged $this task")

    LOG.assertTrue(!(startCondition != null && oldTask.startCondition != null), "Merge of two start conditions is not implemented")
    val mergedHideProgress: Boolean?
    if (shouldHideProgressInSmartMode == null) {
      mergedHideProgress = oldTask.shouldHideProgressInSmartMode
    }
    else if (oldTask.shouldHideProgressInSmartMode != null) {
      assert(oldTask.shouldHideProgressInSmartMode != null && shouldHideProgressInSmartMode != null)
      mergedHideProgress = (shouldHideProgressInSmartMode && oldTask.shouldHideProgressInSmartMode)
    }
    else {
      mergedHideProgress = shouldHideProgressInSmartMode
    }

    val mergedScanningHistoryFuture = SettableFuture.create<ProjectScanningHistory>()
    futureScanningHistory.setFuture(mergedScanningHistoryFuture)         // fixme: non-idempotent
    oldTask.futureScanningHistory.setFuture(mergedScanningHistoryFuture) // fixme: non-idempotent

    val triggerA = forceReindexingTrigger
    val triggerB = oldTask.forceReindexingTrigger
    val mergedPredicate = Predicate { f: IndexedFile ->
      (triggerA != null && triggerA.test(f)) ||
      (triggerB != null && triggerB.test(f))
    }
    return UnindexedFilesScanner(
      myProject,
      myStartSuspended,
      false,
      false,
      mergeIterators(predefinedIndexableFilesIterators, oldTask.predefinedIndexableFilesIterators),
      StatusMark.mergeStatus(myProvidedStatusMark, oldTask.myProvidedStatusMark),
      reason,
      ScanningType.merge(scanningType, oldTask.scanningType),
      startCondition ?: oldTask.startCondition,
      mergedHideProgress, mergedScanningHistoryFuture, mergedPredicate
    )
  }

  private fun scan(indicator: CheckPauseOnlyProgressIndicator,
                   progressReporter: IndexingProgressReporter,
                   markRef: Ref<StatusMark>) {
    val orderedProviders: List<IndexableFilesIterator> = getIndexableFilesIterators(markRef)

    markStage(ProjectScanningHistoryImpl.Stage.CollectingIndexableFiles) {
      val projectIndexingDependenciesService = myProject.getService(ProjectIndexingDependenciesService::class.java)
      val scanningRequest = if (myOnProjectOpen) projectIndexingDependenciesService.newScanningTokenOnProjectOpen() else projectIndexingDependenciesService.newScanningToken()

      try {
        ScanningSession(myProject, scanningHistory, forceReindexingTrigger, myFilterHandler, indicator, progressReporter, scanningRequest)
          .collectIndexableFilesConcurrently(orderedProviders)
      }
      finally {
        ReadAction.run<Throwable> {
          // read action ensures that service won't be disposed and storage inside won't be closed
          myProject.getServiceIfCreated(ProjectIndexingDependenciesService::class.java)
            ?.completeToken(scanningRequest, isFullIndexUpdate())
        }
      }
    }

    LOG.info(getLogScanningCompletedStageMessage())
  }

  private fun getIndexableFilesIterators(markRef: Ref<StatusMark>) =
    markStage(ProjectScanningHistoryImpl.Stage.CreatingIterators) {
      if (predefinedIndexableFilesIterators == null) {
        val pair = collectProviders(myProject, myIndex)
        markRef.set(pair.second)
        pair.first
      }
      else {
        predefinedIndexableFilesIterators
      }
    }

  internal suspend fun applyDelayedPushOperations() {
    markStageSus(ProjectScanningHistoryImpl.Stage.DelayedPushProperties) {
      val pusher = blockingContext { PushedFilePropertiesUpdater.getInstance(myProject) }
      if (pusher is PushedFilePropertiesUpdaterImpl) {
        pusher.performDelayedPushTasks()
      }
    }
  }

  private fun scanAndUpdateUnindexedFiles(indicator: CheckPauseOnlyProgressIndicator,
                                          progressReporter: IndexingProgressReporter,
                                          markRef: Ref<StatusMark>) {
    try {
      if (!IndexInfrastructure.hasIndices()) {
        return
      }
      scanUnindexedFiles(indicator, progressReporter, markRef)
    }
    finally {
      // Scanning may throw exception (or error).
      // In this case, we should either clear or flush the indexing queue; otherwise, dumb mode will not end in the project.
      if (flushQueueAfterScanning) {
        flushPerProjectIndexingQueue()
      }

      (myProject as UserDataHolderEx).replace(FIRST_SCANNING_REQUESTED, FirstScanningState.REQUESTED, FirstScanningState.PERFORMED)
    }
  }

  private fun scanUnindexedFiles(indicator: CheckPauseOnlyProgressIndicator,
                                 progressReporter: IndexingProgressReporter,
                                 markRef: Ref<StatusMark>) {
    LOG.info("Started scanning for indexing of " + myProject.name + ". Reason: " + indexingReason)

    indicator.onPausedStateChanged { paused: Boolean ->
      if (paused) {
        scanningHistory.suspendStages(Instant.now())
      }
      else {
        scanningHistory.stopSuspendingStages(Instant.now())
      }
    }

    if (myStartSuspended) {
      freezeUntilAllowed()
    }

    progressReporter.setText(IndexingBundle.message("progress.indexing.scanning"))

    if (isFullIndexUpdate()) {
      myIndex.clearIndicesIfNecessary()
    }

    scan(indicator, progressReporter, markRef)

    // the full VFS refresh makes sense only after it's loaded, i.e., after scanning files to index is finished
    val service = myProject.getService(InitialVfsRefreshService::class.java)
    if (ApplicationManager.getApplication().isCommandLine && !CoreProgressManager.shouldKeepTasksAsynchronousInHeadlessMode()) {
      service.runInitialVfsRefresh()
    }
    else {
      service.scheduleInitialVfsRefresh()
    }
  }

  private fun freezeUntilAllowed() {
    val latch = CountDownLatch(1)
    ProgressManager.getInstance().run(
      object : Task.Backgroundable(myProject, IndexingBundle.message("progress.indexing.started.as.suspended"), true) {
        override fun run(indicator1: ProgressIndicator) {
          try {
            while (true) {
              LockSupport.parkNanos(100000)
              ProgressManager.checkCanceled()
            }
          }
          finally {
            latch.countDown()
          }
        }
      }
    )
    ProgressIndicatorUtils.awaitWithCheckCanceled(latch)
  }

  private fun flushPerProjectIndexingQueue() {
    myProject.getService(PerProjectIndexingQueue::class.java).flushNow(this.indexingReason)
  }

  internal class ScanningSession(private val project: Project,
                                private val scanningHistory: ProjectScanningHistoryImpl,
                                private val forceReindexingTrigger: Predicate<IndexedFile>?,
                                private val filterHandler: FilesFilterScanningHandler,
                                private val indicator: CheckPauseOnlyProgressIndicator,
                                private val progressReporter: IndexingProgressReporter,
                                private val scanningRequest: ScanningRequestToken) {

    fun collectIndexableFilesConcurrently(providers: List<IndexableFilesIterator>) {
      if (providers.isEmpty()) {
        return
      }

      val sessions =
        IndexableFileScanner.EP_NAME.extensionList.map { scanner: IndexableFileScanner -> scanner.startSession(project) }

      val indexableFilesDeduplicateFilter = IndexableFilesDeduplicateFilter.create()

      LOG.info("Scanning of " + project.name + " uses " + UnindexedFilesUpdater.getNumberOfScanningThreads() + " scanning threads")
      progressReporter.setText(IndexingBundle.message("progress.indexing.scanning"))
      progressReporter.setSubTasksCount(providers.size)

      val sharedExplanationLogger = IndexingReasonExplanationLogger()
      val providersToCheck = Channel<IndexableFilesIterator>(capacity = RENDEZVOUS)

      runBlockingCancellable {
        async {
          for (provider in providers) {
            providersToCheck.send(provider)
          }
          providersToCheck.close()
        }

        repeatTaskConcurrently(continueOnException = true, SCANNING_DISPATCHER, SCANNING_PARALLELISM) {
          val provider = providersToCheck.receiveCatching().getOrNull() ?: return@repeatTaskConcurrently false
          scanSingleProvider(provider, sessions, indexableFilesDeduplicateFilter, sharedExplanationLogger)

          return@repeatTaskConcurrently true
        }
      }
    }

    private suspend fun scanSingleProvider(provider: IndexableFilesIterator,
                                           sessions: List<ScanSession>,
                                           indexableFilesDeduplicateFilter: IndexableFilesDeduplicateFilter,
                                           sharedExplanationLogger: IndexingReasonExplanationLogger) {
      val scanningStatistics = ScanningStatistics(provider.debugName)
      scanningStatistics.setProviderRoots(provider, project)
      val origin = provider.origin

      val fileScannerVisitors = blockingContext { sessions.mapNotNull { s: ScanSession -> s.createVisitor(origin) } }
      val thisProviderDeduplicateFilter =
        IndexableFilesDeduplicateFilter.createDelegatingTo(indexableFilesDeduplicateFilter)

      val providerScanningStartTime = System.nanoTime()
      try {
        progressReporter.getSubTaskReporter().use { subTaskReporter ->
          subTaskReporter.setText(provider.rootsScanningProgressText)
          val files: ArrayDeque<VirtualFile> = getFilesToScan(fileScannerVisitors, scanningStatistics, provider, thisProviderDeduplicateFilter)
          scanFiles(provider, scanningStatistics, sharedExplanationLogger, files)
        }
      }
      catch (e: Exception) {
        scanningRequest.markUnsuccessful()

        // Some code doesn't care if we are inside a non-cancellable section, or in a coroutine.
        // E.g., ComponentManagerImpl.doGetService does `throw new PCE("out-of-thin")`.
        // Handle all these PCE and CE as a valid cancellation.
        if (coroutineContext.isActive && (e is ProcessCanceledException || e is CancellationException)) {
          coroutineContext.cancel()
        }
        checkCanceled()

        // CollectingIterator should skip failing files by itself. But if provider.iterateFiles cannot iterate files and throws exception,
        // we want to ignore the whole origin and let other origins complete normally.
        LOG.error("Error while scanning files of ${provider.debugName}. To reindex files under this origin IDEA has to be restarted", e)
      }
      finally {
        scanningStatistics.totalOneThreadTimeWithPauses = System.nanoTime() - providerScanningStartTime
        scanningStatistics.numberOfSkippedFiles = thisProviderDeduplicateFilter.numberOfSkippedFiles
        scanningHistory.addScanningStatistics(scanningStatistics)
      }
    }

    private suspend fun scanFiles(provider: IndexableFilesIterator,
                                  scanningStatistics: ScanningStatistics,
                                  sharedExplanationLogger: IndexingReasonExplanationLogger,
                                  files: ArrayDeque<VirtualFile>) {
      project.getService(PerProjectIndexingQueue::class.java)
        .getSink(provider, scanningHistory.scanningSessionId).use { perProviderSink ->
          scanningStatistics.startFileChecking()
          try {
            readAction {
              val finder = UnindexedFilesFinder(project, sharedExplanationLogger, forceReindexingTrigger,
                                                scanningRequest, filterHandler)
              val rootIterator = SingleProviderIterator(project, indicator, provider, finder,
                                                        scanningStatistics, perProviderSink)
              if (!rootIterator.mayBeUsed()) {
                LOG.warn("Iterator based on $provider can't be used.")
                return@readAction
              }
              while (files.isNotEmpty()) {
                val file = files.removeFirst()
                try {
                  if (file.isValid)
                    rootIterator.processFile(file)
                }
                catch (e: ProcessCanceledException) {
                  files.addFirst(file)
                  throw e
                }
              }
            }
          }
          finally {
            scanningStatistics.tryFinishFilesChecking()
          }
          perProviderSink.commit()
        }
    }

    private suspend fun getFilesToScan(fileScannerVisitors: List<IndexableFileScanner.IndexableFileVisitor>,
                                       scanningStatistics: ScanningStatistics,
                                       provider: IndexableFilesIterator,
                                       thisProviderDeduplicateFilter: IndexableFilesDeduplicateFilter): ArrayDeque<VirtualFile> {
      val files: ArrayDeque<VirtualFile> = ArrayDeque(1024)
      val singleProviderIteratorFactory = ContentIterator { fileOrDir: VirtualFile ->
        // we apply scanners here, because scanners may mark directory as excluded, and we should skip excluded subtrees
        // (e.g., JSDetectingProjectFileScanner.startSession will exclude "node_modules" directories during scanning)
        PushedFilePropertiesUpdaterImpl.applyScannersToFile(fileOrDir, fileScannerVisitors)
        files.add(fileOrDir)
      }

      scanningStatistics.startVfsIterationAndScanningApplication()
      try {
        blockingContext {
          provider.iterateFiles(project, singleProviderIteratorFactory, thisProviderDeduplicateFilter)
        }
      }
      finally {
        scanningStatistics.tryFinishVfsIterationAndScanningApplication()
      }
      return files
    }

    private suspend fun repeatTaskConcurrently(continueOnException: Boolean,
                                               context: CoroutineContext,
                                               parallelism: Int,
                                               block: suspend () -> Boolean) {
      withContext(context) {
        repeat(parallelism) {
          async {
            var shouldContinue: Boolean
            do {
              checkCanceled()
              try {
                shouldContinue = block.invoke()
              }
              catch (t: Throwable) {
                // Exception from the task should not finish coroutine execution. Coroutine should proceed with the following task.
                // We ignore all the exceptions. If the task is indeed canceled, checkCanceled will throw.
                shouldContinue = continueOnException
                checkCanceled()
                if (t is ControlFlowException) {
                  LOG.warn("Unexpected exception during scanning: ${t.message}")
                }
                else {
                  LOG.error("Unexpected exception during scanning (ignored)", t)
                }
              }
            }
            while (shouldContinue)
          }
        }
      }
    }
  }

  fun perform() {
    perform(object : CheckPauseOnlyProgressIndicator {
      override fun onPausedStateChanged(action: Consumer<Boolean>) {}

      override fun freezeIfPaused() {}
    }, IndexingProgressReporter(/* no visible progress */))
  }

  fun perform(indicator: CheckPauseOnlyProgressIndicator, progressReporter: IndexingProgressReporter) {
    LOG.assertTrue(myProject.getUserData(INDEX_UPDATE_IN_PROGRESS) != true, "Scanning is already in progress")
    getInstance().getTracer(Indexes).spanBuilder("UnindexedFilesScanner.perform").use {
      try {
        myProject.putUserData(INDEX_UPDATE_IN_PROGRESS, true)
        myFilterHandler.scanningStarted(myProject, isFullIndexUpdate())
        val diagnosticDumper = IndexDiagnosticDumper.getInstance()
        diagnosticDumper.onScanningStarted(scanningHistory) //todo[lene] 1
        try {
          performScanningAndIndexing(indicator, progressReporter)
        }
        finally {
          diagnosticDumper.onScanningFinished(scanningHistory) //todo[lene] 1
        }
        futureScanningHistory.set(scanningHistory)
      }
      catch (t: Throwable) {
        futureScanningHistory.setException(t)
        throw t
      }
      finally {
        myProject.putUserData(INDEX_UPDATE_IN_PROGRESS, false)
        myFilterHandler.scanningCompleted(myProject)
        LOG.assertTrue(futureScanningHistory.isDone, "futureScanningHistory.isDone should be true")
      }
    }
  }

  protected fun performScanningAndIndexing(indicator: CheckPauseOnlyProgressIndicator,
                                           progressReporter: IndexingProgressReporter): ProjectScanningHistory {
    if (ApplicationManager.getApplication().isUnitTestMode && DELAY_IN_TESTS_MS > 0) {
      // This is to ease discovering races, when a test needs indexes, but forgot to wait for smart mode (see IndexingTestUtil).
      // It's better to install assert into FileBasedIndexImpl about access without explicit "wait" or "nowait" action, but this is
      // not feasible at the moment. At the moment, just keep things easy. Most tests cannot tolerate DELAY_IN_TESTS_MS delay.
      // Test will run a bit slower, but behavior will be more stable
      LockSupport.parkNanos(DELAY_IN_TESTS_MS * 1000000L)
    }
    myIndex.loadIndexes()
    myIndex.registeredIndexes.waitUntilAllIndicesAreInitialized() // wait until stale ids are deleted
    if (startCondition != null) { // wait until indexes for dirty files are cleared
      ProgressIndicatorUtils.awaitWithCheckCanceled(startCondition)
    }
    // Not sure that ensureUpToDate is really needed, but it wouldn't hurt to clear up queue not from EDT
    // It was added in this commit: 'Process vfs events asynchronously (IDEA-109525), first cut Maxim.Mossienko 13.11.16, 14:15'
    myIndex.changedFilesCollector.ensureUpToDate()
    val markRef = Ref<StatusMark>()
    try {
      ProjectScanningHistoryImpl.startDumbModeBeginningTracking(myProject, scanningHistory)
      (GistManager.getInstance() as GistManagerImpl).runWithMergingDependentCacheInvalidations {
        scanAndUpdateUnindexedFiles(indicator, progressReporter, markRef)
      }
    }
    catch (e: Throwable) {
      scanningHistory.setWasInterrupted()
      if (e is ControlFlowException) {
        LOG.info("Cancelled indexing of " + myProject.name)
      }
      throw e
    }
    finally {
      ProjectScanningHistoryImpl.finishDumbModeBeginningTracking(myProject)
      if (DependenciesIndexedStatusService.shouldBeUsed() && IndexInfrastructure.hasIndices()) {
        DependenciesIndexedStatusService.getInstance(myProject)
          .indexingFinished(!scanningHistory.times.wasInterrupted, markRef.get())
      }
    }
    return scanningHistory
  }

  override fun toString(): String {
    val partialInfo = if (predefinedIndexableFilesIterators != null) ", " + predefinedIndexableFilesIterators.size + " iterators" else ""
    return "UnindexedFilesScanner[" + myProject.name + partialInfo + "]"
  }

  fun queue(): Future<ProjectScanningHistory> {
    // Delay scanning tasks until after all the scheduled dumb tasks are finished.
    // For example, PythonLanguageLevelPusher.initExtra is invoked from RequiredForSmartModeActivity and may submit additional dumb tasks.
    // We want scanning to start after all these "extra" dumb tasks are finished.
    // Note that a project may become dumb/smart immediately after the check
    // If a project becomes smart, in the worst case, we'll trigger additional short dumb mode.
    // If a project becomes dumb, not a problem at all - we'll schedule a scanning task out of dumb mode either way.
    if (DumbService.isDumb(myProject) && Registry.`is`("scanning.waits.for.non.dumb.mode", true)) {
      object : DumbModeTask() {
        override fun performInDumbMode(indicator: ProgressIndicator) {
          UnindexedFilesScannerExecutor.getInstance(myProject).submitTask(this@UnindexedFilesScanner)
        }
      }.queue(myProject)
    }
    else {
      UnindexedFilesScannerExecutor.getInstance(myProject).submitTask(this)
    }
    return futureScanningHistory
  }

  override fun close() {
    if (!myProject.isDisposed) {
      myProject.getServiceIfCreated(ProjectIndexingDependenciesService::class.java)?.completeToken(taskToken)
    }
  }

  @TestOnly
  fun setFlushQueueAfterScanning(flushQueueAfterScanning: Boolean) {
    this.flushQueueAfterScanning = flushQueueAfterScanning
  }

  // we declare separate method instead of `inline markStage` to make stacktraces more readable
  // and avoid warning about ProgressManager.checkCanceled() being called from suspend context
  private suspend fun <T> markStageSus(scanningStage: ProjectScanningHistoryImpl.Stage, block: suspend () -> T): T {
    checkCanceled()
    LOG.info("[${myProject.locationHash}], scanning stage: $scanningStage")
    val scanningStageTime = Instant.now()
    try {
      scanningHistory.startStage(scanningStage, scanningStageTime)
      return block()
    }
    finally {
      scanningHistory.stopStage(scanningStage, scanningStageTime)
      checkCanceled()
    }
  }

  @RequiresBlockingContext
  private fun <T> markStage(scanningStage: ProjectScanningHistoryImpl.Stage, block: () -> T): T {
    ProgressManager.checkCanceled()
    LOG.info("[${myProject.locationHash}], scanning stage: $scanningStage")
    val scanningStageTime = Instant.now()
    try {
      scanningHistory.startStage(scanningStage, scanningStageTime)
      return block()
    }
    finally {
      scanningHistory.stopStage(scanningStage, scanningStageTime)
      ProgressManager.checkCanceled()
    }
  }

  private fun getLogScanningCompletedStageMessage(): String {
    val statistics = scanningHistory.scanningStatistics
    val numberOfScannedFiles = statistics.map(JsonScanningStatistics::numberOfScannedFiles).sum()
    val numberOfFilesForIndexing = statistics.map(JsonScanningStatistics::numberOfFilesForIndexing).sum()
    return "Scanning completed for " + scanningHistory.project.name + '.' +
           " Number of scanned files: " + numberOfScannedFiles + "; number of files for indexing: " + numberOfFilesForIndexing
  }

  companion object {
    private val DELAY_IN_TESTS_MS = SystemProperties.getIntProperty("scanning.delay.before.start.in.tests.ms", 0)

    private val SCANNING_PARALLELISM = UnindexedFilesUpdater.getNumberOfScanningThreads()
    private val BLOCKING_PROVIDERS_ITERATOR_PARALLELISM = SCANNING_PARALLELISM

    // We still have a lot of IO during scanning, so Default dispatcher might be not the best choice at the moment.
    // (this is my best guess, not confirmed by any experiment - you are welcome to experiment with dispatchers if you wish)
    @OptIn(ExperimentalCoroutinesApi::class)
    private val SCANNING_DISPATCHER = Dispatchers.IO.limitedParallelism(SCANNING_PARALLELISM)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val BLOCKING_PROVIDERS_ITERATOR_DISPATCHER = Dispatchers.IO.limitedParallelism(BLOCKING_PROVIDERS_ITERATOR_PARALLELISM)

    @JvmField
    val LOG: Logger = Logger.getInstance(UnindexedFilesScanner::class.java)

    @JvmField
    @VisibleForTesting
    @Volatile
    var ourTestMode: TestMode? = null

    private val INDEX_UPDATE_IN_PROGRESS = Key.create<Boolean>("INDEX_UPDATE_IN_PROGRESS")

    private fun mergeIterators(iterators: List<IndexableFilesIterator>?,
                               otherIterators: List<IndexableFilesIterator>?): List<IndexableFilesIterator>? {
      if (iterators == null || otherIterators == null) return null
      val uniqueIterators: MutableMap<IndexableSetOrigin, IndexableFilesIterator> = LinkedHashMap()
      for (iterator in iterators) {
        uniqueIterators.putIfAbsent(iterator.origin, iterator)
      }
      for (iterator in otherIterators) {
        uniqueIterators.putIfAbsent(iterator.origin, iterator)
      }
      return ArrayList(uniqueIterators.values)
    }

    @JvmStatic
    fun isIndexUpdateInProgress(project: Project): Boolean {
      return project.getUserData(INDEX_UPDATE_IN_PROGRESS) == true
    }

    private fun collectProviders(project: Project, index: FileBasedIndexImpl): Pair<List<IndexableFilesIterator>, StatusMark?> {
      val cache = DependenciesIndexedStatusService.shouldBeUsed()
      val originalOrderedProviders: List<IndexableFilesIterator>
      var mark: StatusMark? = null
      if (cache) {
        DependenciesIndexedStatusService.getInstance(project).startCollectingStatus()
      }
      try {
        originalOrderedProviders = index.getIndexableFilesProviders(project)
      }
      finally {
        if (cache) {
          mark = DependenciesIndexedStatusService.getInstance(project).finishCollectingStatus()
        }
      }

      val orderedProviders: MutableList<IndexableFilesIterator> = ArrayList()
      originalOrderedProviders
        .filter { p: IndexableFilesIterator -> p.origin !is SdkOrigin }
        .toCollection(orderedProviders)

      originalOrderedProviders
        .filter { p: IndexableFilesIterator -> p.origin is SdkOrigin }
        .toCollection(orderedProviders)

      return Pair(orderedProviders, mark)
    }
  }
}
