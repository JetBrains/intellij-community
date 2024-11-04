// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.*
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.FilesScanningTask
import com.intellij.openapi.project.InitialVfsRefreshService
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.UnindexedFilesScannerExecutor
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdaterImpl
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.diagnostic.telemetry.Indexes
import com.intellij.platform.diagnostic.telemetry.TelemetryManager.Companion.getInstance
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.util.coroutines.forEachConcurrent
import com.intellij.util.gist.GistManager
import com.intellij.util.gist.GistManagerImpl
import com.intellij.util.indexing.FilesFilterScanningHandler.IdleFilesFilterScanningHandler
import com.intellij.util.indexing.FilesFilterScanningHandler.UpdatingFilesFilterScanningHandler
import com.intellij.util.indexing.IndexingProgressReporter.CheckPauseOnlyProgressIndicator
import com.intellij.util.indexing.dependencies.FileIndexingStamp
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
import com.intellij.util.indexing.roots.origin.GenericContentEntityOrigin
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.io.Closeable
import java.time.Instant
import java.util.concurrent.Future
import java.util.function.BiPredicate
import kotlin.coroutines.coroutineContext

@ApiStatus.Internal
sealed interface ScanningParameters

internal object CancelledScanning: ScanningParameters

@ApiStatus.Internal
class ScanningIterators(
  internal val indexingReason: String,
  internal val predefinedIndexableFilesIterators: List<IndexableFilesIterator>? = null,
  internal val mark: StatusMark? = null,
  internal val scanningType: ScanningType = if (predefinedIndexableFilesIterators == null) ScanningType.FULL else ScanningType.PARTIAL,
): ScanningParameters {

  init {
    UnindexedFilesScanner.LOG.assertTrue(this.predefinedIndexableFilesIterators == null || !predefinedIndexableFilesIterators.isEmpty())
  }

  internal fun isFullIndexUpdate(): Boolean {
    return predefinedIndexableFilesIterators == null
  }
}

@ApiStatus.Internal
class UnindexedFilesScanner @JvmOverloads constructor(
  private val myProject: Project,
  private val myOnProjectOpen: Boolean,
  isIndexingFilesFilterUpToDate: Boolean,
  private val startCondition: Future<*>?,
  private val shouldHideProgressInSmartMode: Boolean? = null,
  private val forceReindexingTrigger: BiPredicate<IndexedFile, FileIndexingStamp>? = null,
  private val allowCheckingForOutdatedIndexesUsingFileModCount: Boolean = false,
  internal val scanningParameters: Deferred<ScanningParameters>,
) : FilesScanningTask, Closeable {

  enum class TestMode {
    PUSHING, PUSHING_AND_SCANNING
  }

  private val myIndex = FileBasedIndex.getInstance() as FileBasedIndexImpl
  private val myFilterHandler: FilesFilterScanningHandler
  private val taskToken = myProject.getService(ProjectIndexingDependenciesService::class.java).newIncompleteTaskToken()
  private lateinit var scanningHistory: ProjectScanningHistoryImpl

  constructor(project: Project, scanningParameters: Deferred<ScanningParameters>)
    : this(myProject = project,
           myOnProjectOpen = false,
           isIndexingFilesFilterUpToDate = false,
           startCondition = null,
           scanningParameters = scanningParameters)

  constructor(
    project: Project,
    indexingReason: String,
    shouldHideProgressInSmartMode: Boolean?,
  ) : this(myProject = project,
           myOnProjectOpen = false,
           isIndexingFilesFilterUpToDate = false,
           startCondition = null,
           shouldHideProgressInSmartMode = shouldHideProgressInSmartMode,
           forceReindexingTrigger = null,
           scanningParameters = CompletableDeferred(ScanningIterators(indexingReason)))

  constructor(project: Project, indexingReason: String)
    : this(project, CompletableDeferred(ScanningIterators(indexingReason)))

  constructor(
    project: Project,
    predefinedIndexableFilesIterators: List<IndexableFilesIterator>?,
    indexingReason: String,
  ) : this(project, CompletableDeferred(ScanningIterators(indexingReason, predefinedIndexableFilesIterators)))

  @TestOnly
  constructor(project: Project)
    : this(project, CompletableDeferred(ScanningIterators("<unknown>")))

  init {
    val filterHolder = myIndex.indexableFilesFilterHolder
    myFilterHandler = if (isIndexingFilesFilterUpToDate) IdleFilesFilterScanningHandler()
    else UpdatingFilesFilterScanningHandler(filterHolder)
  }

  private fun defaultHideProgressInSmartModeStrategy(): Boolean {
    return Registry.`is`("scanning.hide.progress.in.smart.mode", true) &&
           myProject.getUserData(FIRST_SCANNING_REQUESTED) == FirstScanningState.REQUESTED
  }

  fun shouldHideProgressInSmartMode(): Boolean {
    return shouldHideProgressInSmartMode ?: defaultHideProgressInSmartModeStrategy()
  }

  /**
   * We may not have information about whether it's a full update or not, in which case we return null
   */
  override fun isFullIndexUpdate(): Boolean? {
    val parameters = scanningParameters.getCompletedSafe()
    if (parameters == null) {
      return null
    }
    return parameters is ScanningIterators && parameters.isFullIndexUpdate()
  }

  internal suspend fun getScanningParameters(): ScanningParameters {
    return scanningParameters.await()
  }

  fun tryMergeWith(oldTask: FilesScanningTask, mergeScope: CoroutineScope): UnindexedFilesScanner {
    oldTask as UnindexedFilesScanner

    LOG.assertTrue(myProject == oldTask.myProject)

    val mergedParameters = mergeScope.async {
      val parameters = scanningParameters.await()
      val oldParameters = oldTask.scanningParameters.await()

      if (parameters is CancelledScanning) return@async oldParameters
      if (oldParameters is CancelledScanning) return@async parameters

      parameters as ScanningIterators
      oldParameters as ScanningIterators

      val reason = if (oldParameters.isFullIndexUpdate()) {
        oldParameters.indexingReason
      }
      else if (parameters.isFullIndexUpdate()) {
        parameters.indexingReason
      }
      else {
        "Merged " + parameters.indexingReason.removePrefix("Merged ") +
        " with " + oldParameters.indexingReason.removePrefix("Merged ")
      }

      LOG.debug("Merged $this task")

      ScanningIterators(
        reason,
        mergeIterators(parameters.predefinedIndexableFilesIterators, oldParameters.predefinedIndexableFilesIterators),
        StatusMark.mergeStatus(parameters.mark, oldParameters.mark),
        ScanningType.merge(parameters.scanningType, oldParameters.scanningType),
      )
    }

    LOG.assertTrue(!(startCondition != null && oldTask.startCondition != null), "Merge of two start conditions is not implemented")
    val mergedHideProgress: Boolean?
    if (shouldHideProgressInSmartMode == null) {
      mergedHideProgress = oldTask.shouldHideProgressInSmartMode
    }
    else if (oldTask.shouldHideProgressInSmartMode != null) {
      mergedHideProgress = (shouldHideProgressInSmartMode && oldTask.shouldHideProgressInSmartMode)
    }
    else {
      mergedHideProgress = shouldHideProgressInSmartMode
    }

    val triggerA = forceReindexingTrigger
    val triggerB = oldTask.forceReindexingTrigger
    val mergedPredicate = BiPredicate { f: IndexedFile, stamp: FileIndexingStamp ->
      (triggerA != null && triggerA.test(f, stamp)) ||
      (triggerB != null && triggerB.test(f, stamp))
    }
    return UnindexedFilesScanner(
      myProject,
      false,
      false,
      startCondition ?: oldTask.startCondition,
      mergedHideProgress,
      mergedPredicate,
      allowCheckingForOutdatedIndexesUsingFileModCount || oldTask.allowCheckingForOutdatedIndexesUsingFileModCount,
      mergedParameters,
    )
  }

  private fun scan(
    indicator: CheckPauseOnlyProgressIndicator,
    progressReporter: IndexingProgressReporter,
    markRef: Ref<StatusMark>,
    scanningIterators: ScanningIterators,
  ) {
    val orderedProviders: List<IndexableFilesIterator> = getIndexableFilesIterators(markRef, scanningIterators)

    markStage(ProjectScanningHistoryImpl.Stage.CollectingIndexableFiles) {
      val projectIndexingDependenciesService = myProject.getService(ProjectIndexingDependenciesService::class.java)
      val scanningRequest = if (myOnProjectOpen) projectIndexingDependenciesService.newScanningTokenOnProjectOpen(allowCheckingForOutdatedIndexesUsingFileModCount)
      else projectIndexingDependenciesService.newScanningToken()

      try {
        ScanningSession(myProject, scanningHistory, forceReindexingTrigger, myFilterHandler, indicator, progressReporter, scanningRequest)
          .collectIndexableFilesConcurrently(orderedProviders)
      }
      finally {
        ReadAction.run<Throwable> {
          // read action ensures that service won't be disposed and storage inside won't be closed
          myProject.getServiceIfCreated(ProjectIndexingDependenciesService::class.java)
            ?.completeToken(scanningRequest, scanningIterators.isFullIndexUpdate())
        }
      }
    }

    LOG.info(getLogScanningCompletedStageMessage())
  }

  private fun getIndexableFilesIterators(markRef: Ref<StatusMark>, scanningIterators: ScanningIterators) =
    markStage(ProjectScanningHistoryImpl.Stage.CreatingIterators) {
      val predefinedIndexableFilesIterators = scanningIterators.predefinedIndexableFilesIterators
      if (predefinedIndexableFilesIterators == null) {
        val pair = collectProviders(myProject, myIndex)
        markRef.set(pair.second)
        pair.first
      }
      else {
        predefinedIndexableFilesIterators
      }
    }

  internal suspend fun applyDelayedPushOperations(scanningHistory: ProjectScanningHistoryImpl) {
    this.scanningHistory = scanningHistory
    markStageSus(ProjectScanningHistoryImpl.Stage.DelayedPushProperties) {
      val pusher = blockingContext { PushedFilePropertiesUpdater.getInstance(myProject) }
      if (pusher is PushedFilePropertiesUpdaterImpl) {
        pusher.performDelayedPushTasks()
      }
    }
  }

  @TestOnly
  fun getIndexingReasonBlocking(): String {
    return runBlockingCancellable { scanningParameters.await() as ScanningIterators }.indexingReason
  }

  @TestOnly
  fun getPredefinedIndexableFileIteratorsBlocking(): List<IndexableFilesIterator>? {
    return runBlockingCancellable { scanningParameters.await() as ScanningIterators }.predefinedIndexableFilesIterators
  }

  @TestOnly
  fun getScanningTypeBlocking(): ScanningType {
    return runBlockingCancellable { scanningParameters.await() as ScanningIterators }.scanningType
  }

  private fun tryGetPredefinedIndexableFileIterators(): List<IndexableFilesIterator>? {
    val scanningIterators = scanningParameters.getCompletedSafe() ?: return null
    if (scanningIterators !is ScanningIterators) return null
    return scanningIterators.predefinedIndexableFilesIterators
  }

  private fun scanAndUpdateUnindexedFiles(
    indicator: CheckPauseOnlyProgressIndicator,
    progressReporter: IndexingProgressReporter,
    markRef: Ref<StatusMark>,
    scanningIterators: ScanningIterators,
  ) {
    try {
      if (!IndexInfrastructure.hasIndices()) {
        return
      }
      scanUnindexedFiles(indicator, progressReporter, markRef, scanningIterators)
    }
    finally {
      (myProject as UserDataHolderEx).replace(FIRST_SCANNING_REQUESTED, FirstScanningState.REQUESTED, FirstScanningState.PERFORMED)
    }
  }

  private fun scanUnindexedFiles(
    indicator: CheckPauseOnlyProgressIndicator,
    progressReporter: IndexingProgressReporter,
    markRef: Ref<StatusMark>,
    scanningIterators: ScanningIterators,
  ) {
    LOG.info("Started scanning for indexing of " + myProject.name + ". Reason: " + scanningIterators.indexingReason)

    progressReporter.setText(IndexingBundle.message("progress.indexing.scanning"))

    if (scanningIterators.isFullIndexUpdate()) {
      myIndex.clearIndicesIfNecessary()
    }

    scan(indicator, progressReporter, markRef, scanningIterators)

    // the full VFS refresh makes sense only after it's loaded, i.e., after scanning files to index is finished
    val service = myProject.getService(InitialVfsRefreshService::class.java)
    if (ApplicationManager.getApplication().isCommandLine && !CoreProgressManager.shouldKeepTasksAsynchronousInHeadlessMode()) {
      service.runInitialVfsRefresh()
    }
    else {
      service.scheduleInitialVfsRefresh()
    }
  }

  internal class ScanningSession(private val project: Project,
                                 private val scanningHistory: ProjectScanningHistoryImpl,
                                 private val forceReindexingTrigger: BiPredicate<IndexedFile, FileIndexingStamp>?,
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
      /**
       * Always scan `ModuleIndexableFilesIteratorImpl` before `GenericContentEntityIteratorImpl`.
       * The reason is that inside `SingleProviderIterator` we call `FilePropertyPusher`s
       * only if the provider's `origin` is an instance of `ModuleContentOrigin`.
       * If we first scan a file via `GenericContentEntityIteratorImpl`, then the pushers will not be applied, and we will have added it
       * to `indexableFilesDeduplicateFilter` already.
       * By the time we scan it again via `ModuleIndexableFilesIterator` the file will be ignored
       * because of `indexableFilesDeduplicateFilter`, and therefore the `FilePropertyPusher`s will not run for that file.
       */
      val (genericContentEntityProviders, otherProviders) = providers.partition { provider -> provider.origin is GenericContentEntityOrigin }
      collectIndexableFilesConcurrently(otherProviders, sessions, indexableFilesDeduplicateFilter, sharedExplanationLogger)
      collectIndexableFilesConcurrently(genericContentEntityProviders, sessions, indexableFilesDeduplicateFilter, sharedExplanationLogger)
    }

    private fun collectIndexableFilesConcurrently(
      providers: List<IndexableFilesIterator>,
      sessions: List<ScanSession>,
      indexableFilesDeduplicateFilter: IndexableFilesDeduplicateFilter,
      sharedExplanationLogger: IndexingReasonExplanationLogger,
    ) {
      runBlockingCancellable {
        withContext(SCANNING_DISPATCHER) {
          providers.forEachConcurrent(SCANNING_PARALLELISM) { provider ->
            try {
              scanSingleProvider(provider, sessions, indexableFilesDeduplicateFilter, sharedExplanationLogger)
            } catch (t: Throwable) {
              checkCanceled()
              if (t is ControlFlowException) {
                LOG.warn("Unexpected exception during scanning: ${t.message}")
              }
              else {
                LOG.error("Unexpected exception during scanning (ignored)", t)
              }
            }
          }
        }
      }
    }

    private suspend fun scanSingleProvider(
      provider: IndexableFilesIterator,
      sessions: List<ScanSession>,
      indexableFilesDeduplicateFilter: IndexableFilesDeduplicateFilter,
      sharedExplanationLogger: IndexingReasonExplanationLogger,
    ) {
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
        LOG.error("Error while scanning files of ${provider.debugName}. To reindex files under this origin IDE has to be restarted", e)
      }
      finally {
        scanningStatistics.totalOneThreadTimeWithPauses = System.nanoTime() - providerScanningStartTime
        scanningStatistics.numberOfSkippedFiles = thisProviderDeduplicateFilter.numberOfSkippedFiles
        scanningHistory.addScanningStatistics(scanningStatistics)
      }
    }

    private suspend fun scanFiles(
      provider: IndexableFilesIterator,
      scanningStatistics: ScanningStatistics,
      sharedExplanationLogger: IndexingReasonExplanationLogger,
      files: ArrayDeque<VirtualFile>,
    ) {
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
        }
    }

    private suspend fun getFilesToScan(
      fileScannerVisitors: List<IndexableFileScanner.IndexableFileVisitor>,
      scanningStatistics: ScanningStatistics,
      provider: IndexableFilesIterator,
      thisProviderDeduplicateFilter: IndexableFilesDeduplicateFilter,
    ): ArrayDeque<VirtualFile> {
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
  }

  internal fun perform(
    indicator: CheckPauseOnlyProgressIndicator,
    progressReporter: IndexingProgressReporter,
    scanningHistory: ProjectScanningHistoryImpl,
    scanningParameters: ScanningIterators,
  ) {
    getInstance().getTracer(Indexes).spanBuilder("UnindexedFilesScanner.perform").use {
      try {
        this.scanningHistory = scanningHistory
        myFilterHandler.scanningStarted(myProject, scanningParameters.isFullIndexUpdate())
        prepareScanningHistoryAndRun(indicator) {
          waitForPreconditions()
          val markRef = Ref<StatusMark>()
          var successfullyFinished = false
          try {
            (GistManager.getInstance() as GistManagerImpl).runWithMergingDependentCacheInvalidations {
              scanAndUpdateUnindexedFiles(indicator, progressReporter, markRef, scanningParameters)
            }
            successfullyFinished = true
          }
          finally {
            if (DependenciesIndexedStatusService.shouldBeUsed() && IndexInfrastructure.hasIndices()) {
              DependenciesIndexedStatusService.getInstance(myProject).indexingFinished(successfullyFinished, markRef.get())
            }
          }
        }
      }
      finally {
        myFilterHandler.scanningCompleted(myProject)
      }
    }
  }

  private fun prepareScanningHistoryAndRun(indicator: CheckPauseOnlyProgressIndicator, block: () -> Unit) {
    indicator.onPausedStateChanged { paused: Boolean ->
      if (paused) {
        scanningHistory.suspendStages(Instant.now())
      }
      else {
        scanningHistory.stopSuspendingStages(Instant.now())
      }
    }

    val diagnosticDumper = IndexDiagnosticDumper.getInstance()
    diagnosticDumper.onScanningStarted(scanningHistory)
    try {
      getInstance().getTracer(Indexes).spanBuilder("InternalSpanForScanningDiagnostic").use {
        ProjectScanningHistoryImpl.startDumbModeBeginningTracking(myProject, scanningHistory)
        try {
          block()
        }
        finally {
          ProjectScanningHistoryImpl.finishDumbModeBeginningTracking(myProject)
        }
      }
    }
    catch (e: Throwable) {
      scanningHistory.setWasInterrupted()
      throw e
    }
    finally {
      diagnosticDumper.onScanningFinished(scanningHistory)
    }
  }

  private fun waitForPreconditions() {
    myIndex.loadIndexes()
    myIndex.registeredIndexes.waitUntilAllIndicesAreInitialized() // wait until stale ids are deleted
    if (startCondition != null) { // wait until indexes for dirty files are cleared
      ProgressIndicatorUtils.awaitWithCheckCanceled(startCondition)
    }
    // Not sure that ensureUpToDate is really needed, but it wouldn't hurt to clear up queue not from EDT
    // It was added in this commit: 'Process vfs events asynchronously (IDEA-109525), first cut Maxim.Mossienko 13.11.16, 14:15'
    myIndex.changedFilesCollector.ensureUpToDate()
  }

  override fun toString(): String {
    val filesIterators = tryGetPredefinedIndexableFileIterators()
    val partialInfo = if (filesIterators != null) ", " + filesIterators.size + " iterators" else ""
    return "UnindexedFilesScanner[" + myProject.name + partialInfo + "]"
  }

  fun queue(): Future<ProjectScanningHistory> {
    @Suppress("UNCHECKED_CAST")
    return UnindexedFilesScannerExecutor.getInstance(myProject).submitTask(this) as Future<ProjectScanningHistory>
  }

  override fun close() {
    if (!myProject.isDisposed) {
      myProject.getServiceIfCreated(ProjectIndexingDependenciesService::class.java)?.completeToken(taskToken)
    }
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
    private val SCANNING_PARALLELISM = UnindexedFilesUpdater.getNumberOfScanningThreads()

    // We still have a lot of IO during scanning, so Default dispatcher might be not the best choice at the moment.
    // (this is my best guess, not confirmed by any experiment - you are welcome to experiment with dispatchers if you wish)
    @OptIn(ExperimentalCoroutinesApi::class)
    private val SCANNING_DISPATCHER = Dispatchers.IO.limitedParallelism(SCANNING_PARALLELISM)

    @JvmField
    val LOG: Logger = Logger.getInstance(UnindexedFilesScanner::class.java)

    @JvmField
    @VisibleForTesting
    @Volatile
    var ourTestMode: TestMode? = null

    private fun mergeIterators(
      iterators: List<IndexableFilesIterator>?,
      otherIterators: List<IndexableFilesIterator>?,
    ): List<IndexableFilesIterator>? {
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

@OptIn(ExperimentalCoroutinesApi::class)
private fun <T> Deferred<T>.getCompletedSafe(): T? {
  return try {
    getCompleted()
  } catch (_: IllegalStateException) {
    null
  }
}
