// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.google.common.util.concurrent.SettableFuture
import com.intellij.diagnostic.PerformanceWatcher
import com.intellij.diagnostic.PerformanceWatcher.Companion.takeSnapshot
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.*
import com.intellij.openapi.project.DumbService.Companion.isDumb
import com.intellij.openapi.project.UnindexedFilesScannerExecutor.Companion.getInstance
import com.intellij.openapi.project.UnindexedFilesScannerExecutor.Companion.shouldScanInSmartMode
import com.intellij.openapi.roots.ContentIterator
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdaterImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.SystemProperties
import com.intellij.util.gist.GistManager
import com.intellij.util.gist.GistManagerImpl
import com.intellij.util.indexing.FilesFilterScanningHandler.IdleFilesFilterScanningHandler
import com.intellij.util.indexing.FilesFilterScanningHandler.UpdatingFilesFilterScanningHandler
import com.intellij.util.indexing.IndexingProgressReporter.CheckPauseOnlyProgressIndicator
import com.intellij.util.indexing.PerProjectIndexingQueue
import com.intellij.util.indexing.UnindexedFilesScanner
import com.intellij.util.indexing.dependencies.FutureScanningRequestToken
import com.intellij.util.indexing.dependencies.ProjectIndexingDependenciesService
import com.intellij.util.indexing.dependenciesCache.DependenciesIndexedStatusService
import com.intellij.util.indexing.dependenciesCache.DependenciesIndexedStatusService.StatusMark
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper.Companion.getInstance
import com.intellij.util.indexing.diagnostic.ProjectScanningHistory
import com.intellij.util.indexing.diagnostic.ProjectScanningHistoryImpl
import com.intellij.util.indexing.diagnostic.ProjectScanningHistoryImpl.Companion.finishDumbModeBeginningTracking
import com.intellij.util.indexing.diagnostic.ProjectScanningHistoryImpl.Companion.startDumbModeBeginningTracking
import com.intellij.util.indexing.diagnostic.ScanningStatistics
import com.intellij.util.indexing.diagnostic.ScanningType
import com.intellij.util.indexing.diagnostic.ScanningType.Companion.merge
import com.intellij.util.indexing.diagnostic.dto.JsonScanningStatistics
import com.intellij.util.indexing.roots.IndexableFileScanner
import com.intellij.util.indexing.roots.IndexableFileScanner.ScanSession
import com.intellij.util.indexing.roots.IndexableFilesDeduplicateFilter
import com.intellij.util.indexing.roots.IndexableFilesIterator
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin
import com.intellij.util.indexing.roots.kind.SdkOrigin
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Future
import java.util.concurrent.locks.LockSupport
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate
import kotlin.concurrent.Volatile

@ApiStatus.Internal
class UnindexedFilesScanner private constructor(private val myProject: Project,
                                                private val myStartSuspended: Boolean,
                                                private val myOnProjectOpen: Boolean,
                                                isIndexingFilesFilterUpToDate: Boolean,
                                                predefinedIndexableFilesIterators: List<IndexableFilesIterator>?,
                                                mark: StatusMark?,
                                                indexingReason: String?,
                                                scanningType: ScanningType,
                                                startCondition: Future<*>?,
                                                shouldHideProgressInSmartMode: Boolean?,
                                                futureScanningHistory: SettableFuture<ProjectScanningHistory>,
                                                forceReindexTrigger: Predicate<IndexedFile>?) : FilesScanningTask {
  enum class TestMode {
    PUSHING, PUSHING_AND_SCANNING
  }

  private val myIndex = FileBasedIndex.getInstance() as FileBasedIndexImpl
  private val myFilterHandler: FilesFilterScanningHandler
  private val myIndexingReason: String
  private val myScanningType: ScanningType
  private val myStartCondition: Future<*>?
  private val myPusher: PushedFilePropertiesUpdater
  private val myProvidedStatusMark: StatusMark?
  val predefinedIndexableFilesIterators: List<IndexableFilesIterator>?
  private val myFutureScanningRequestToken: FutureScanningRequestToken
  private var flushQueueAfterScanning = true
  private val shouldHideProgressInSmartMode: Boolean?
  private val futureScanningHistory: SettableFuture<ProjectScanningHistory>
  private val forceReindexingTrigger: Predicate<IndexedFile>?

  @TestOnly
  constructor(project: Project) : this(project, false, false, false, null, null, null, ScanningType.FULL, null)


  constructor(project: Project,
              indexingReason: String?) : this(project, false, false, false, null, null, indexingReason, ScanningType.FULL, null)

  constructor(project: Project,
              indexingReason: String?,
              shouldHideProgressInSmartMode: Boolean?) : this(project, false, false, false, null, null, indexingReason, ScanningType.FULL,
                                                              null, shouldHideProgressInSmartMode,
                                                              null)

  constructor(project: Project,
              predefinedIndexableFilesIterators: List<IndexableFilesIterator>?,
              mark: StatusMark?,
              indexingReason: String?) : this(project, false, false, false, predefinedIndexableFilesIterators, mark, indexingReason,
                                              if (predefinedIndexableFilesIterators == null) ScanningType.FULL else ScanningType.PARTIAL,
                                              null)

  @JvmOverloads
  constructor(project: Project,
              startSuspended: Boolean,
              onProjectOpen: Boolean,
              isIndexingFilesFilterUpToDate: Boolean,
              predefinedIndexableFilesIterators: List<IndexableFilesIterator>?,
              mark: StatusMark?,
              indexingReason: String?,
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
    myFilterHandler = if (isIndexingFilesFilterUpToDate
    ) IdleFilesFilterScanningHandler(filterHolder)
    else UpdatingFilesFilterScanningHandler(filterHolder)
    myIndexingReason = if ((indexingReason != null)) indexingReason else "<unknown>"
    myScanningType = scanningType
    myStartCondition = startCondition
    myPusher = PushedFilePropertiesUpdater.getInstance(myProject)
    myProvidedStatusMark = if (predefinedIndexableFilesIterators == null) null else mark
    this.predefinedIndexableFilesIterators = predefinedIndexableFilesIterators
    LOG.assertTrue(this.predefinedIndexableFilesIterators == null || !predefinedIndexableFilesIterators!!.isEmpty())
    LOG.assertTrue( // doing partial scanning of only dirty files on startup
      !myOnProjectOpen ||
      isIndexingFilesFilterUpToDate || this.predefinedIndexableFilesIterators == null,
      "Should request full scanning on project open")
    myFutureScanningRequestToken = myProject.getService(ProjectIndexingDependenciesService::class.java).newFutureScanningToken()

    if (isFullIndexUpdate()) {
      myProject.putUserData(CONTENT_SCANNED, null)
    }
    this.shouldHideProgressInSmartMode = shouldHideProgressInSmartMode
    this.futureScanningHistory = futureScanningHistory
    this.forceReindexingTrigger = forceReindexTrigger
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

  override fun tryMergeWith(oldTask: FilesScanningTask): UnindexedFilesScanner {
    oldTask as UnindexedFilesScanner

    LOG.assertTrue(myProject == oldTask.myProject)
    val reason = if (oldTask.isFullIndexUpdate()) {
      oldTask.myIndexingReason
    }
    else if (isFullIndexUpdate()) {
      myIndexingReason
    }
    else {
      "Merged " + myIndexingReason.removePrefix("Merged ") +
      " with " + oldTask.myIndexingReason.removePrefix("Merged ")
    }
    LOG.debug("Merged $this task")

    oldTask.myFutureScanningRequestToken.markSuccessful()
    myFutureScanningRequestToken.markSuccessful()
    LOG.assertTrue(!(myStartCondition != null && oldTask.myStartCondition != null), "Merge of two start conditions is not implemented")
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
    futureScanningHistory.setFuture(mergedScanningHistoryFuture)
    oldTask.futureScanningHistory.setFuture(mergedScanningHistoryFuture)

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
      merge(oldTask.myScanningType, oldTask.myScanningType),
      myStartCondition ?: oldTask.myStartCondition,
      mergedHideProgress, mergedScanningHistoryFuture, mergedPredicate
    )
  }

  private fun scan(snapshot: PerformanceWatcher.Snapshot,
                   scanningHistory: ProjectScanningHistoryImpl,
                   indicator: CheckPauseOnlyProgressIndicator,
                   progressReporter: IndexingProgressReporter,
                   markRef: Ref<StatusMark>) {
    var snapshot = snapshot
    markStage(scanningHistory, ProjectScanningHistoryImpl.Stage.DelayedPushProperties, true)
    try {
      if (myPusher is PushedFilePropertiesUpdaterImpl) {
        myPusher.performDelayedPushTasks()
      }
    }
    finally {
      markStage(scanningHistory, ProjectScanningHistoryImpl.Stage.DelayedPushProperties, false)
    }
    LOG.info(snapshot.getLogResponsivenessSinceCreationMessage("Performing delayed pushing properties tasks for " + myProject.name))

    snapshot = takeSnapshot()

    if (isFullIndexUpdate()) {
      myIndex.clearIndicesIfNecessary()
    }

    val orderedProviders: List<IndexableFilesIterator>
    markStage(scanningHistory, ProjectScanningHistoryImpl.Stage.CreatingIterators, true)
    try {
      if (predefinedIndexableFilesIterators == null) {
        val pair = collectProviders(myProject, myIndex)
        orderedProviders = pair.first
        markRef.set(pair.second)
      }
      else {
        orderedProviders = predefinedIndexableFilesIterators
      }
    }
    finally {
      markStage(scanningHistory, ProjectScanningHistoryImpl.Stage.CreatingIterators, false)
    }

    markStage(scanningHistory, ProjectScanningHistoryImpl.Stage.CollectingIndexableFiles, true)
    try {
      collectIndexableFilesConcurrently(myProject, indicator, progressReporter, orderedProviders, scanningHistory)
      if (isFullIndexUpdate() || myOnProjectOpen) {
        myProject.putUserData(CONTENT_SCANNED, true)
      }
    }
    finally {
      markStage(scanningHistory, ProjectScanningHistoryImpl.Stage.CollectingIndexableFiles, false)
    }
    val scanningCompletedMessage = getLogScanningCompletedStageMessage(scanningHistory)
    LOG.info(snapshot.getLogResponsivenessSinceCreationMessage(scanningCompletedMessage))
  }

  private fun scanAndUpdateUnindexedFiles(scanningHistory: ProjectScanningHistoryImpl,
                                          indicator: CheckPauseOnlyProgressIndicator,
                                          progressReporter: IndexingProgressReporter,
                                          markRef: Ref<StatusMark>) {
    try {
      if (!IndexInfrastructure.hasIndices()) {
        return
      }
      scanUnindexedFiles(scanningHistory, indicator, progressReporter, markRef)
    }
    finally {
      // Scanning may throw exception (or error).
      // In this case, we should either clear or flush the indexing queue; otherwise, dumb mode will not end in the project.
      if (flushQueueAfterScanning) {
        flushPerProjectIndexingQueue(scanningHistory.scanningReason, indicator)
      }

      (myProject as UserDataHolderEx).replace(FIRST_SCANNING_REQUESTED, FirstScanningState.REQUESTED, FirstScanningState.PERFORMED)
    }
  }

  private fun scanUnindexedFiles(scanningHistory: ProjectScanningHistoryImpl,
                                 indicator: CheckPauseOnlyProgressIndicator,
                                 progressReporter: IndexingProgressReporter,
                                 markRef: Ref<StatusMark>) {
    LOG.info("Started scanning for indexing of " + myProject.name + ". Reason: " + myIndexingReason)

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

    progressReporter.setIndeterminate(true)
    progressReporter.setText(IndexingBundle.message("progress.indexing.scanning"))

    val snapshot = takeSnapshot()
    val scanningLifetime = Disposer.newDisposable()
    try {
      if (!shouldScanInSmartMode()) {
        DumbModeProgressTitle.getInstance(myProject)
          .attachProgressTitleText(IndexingBundle.message("progress.indexing.scanning.title"), scanningLifetime)
      }
      scan(snapshot, scanningHistory, indicator, progressReporter, markRef)
    }
    finally {
      Disposer.dispose(scanningLifetime)
    }

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

  private fun flushPerProjectIndexingQueue(indexingReason: String?, indicator: CheckPauseOnlyProgressIndicator) {
    myProject.getService(PerProjectIndexingQueue::class.java).flushNow(myIndexingReason)
  }

  private fun collectIndexableFilesConcurrently(project: Project,
                                                indicator: CheckPauseOnlyProgressIndicator,
                                                progressReporter: IndexingProgressReporter,
                                                providers: List<IndexableFilesIterator>,
                                                projectScanningHistory: ProjectScanningHistoryImpl) {
    if (providers.isEmpty()) {
      myFutureScanningRequestToken.markSuccessful()
      return
    }
    val projectIndexingDependenciesService = myProject.getService(
      ProjectIndexingDependenciesService::class.java)
    val scanningRequest = if (myOnProjectOpen) projectIndexingDependenciesService.newScanningTokenOnProjectOpen() else projectIndexingDependenciesService.newScanningToken()
    myFutureScanningRequestToken.markSuccessful()
    projectIndexingDependenciesService.completeToken(myFutureScanningRequestToken)

    val sessions =
      IndexableFileScanner.EP_NAME.extensionList.map { scanner: IndexableFileScanner -> scanner.startSession(project) }

    val indexableFilesDeduplicateFilter = IndexableFilesDeduplicateFilter.create()

    progressReporter.setText(IndexingBundle.message("progress.indexing.scanning"))
    progressReporter.setSubTasksCount(providers.size)

    // Workaround for concurrent modification of the [scanningHistory].
    // PushedFilePropertiesUpdaterImpl.invokeConcurrentlyIfPossible may finish earlier than some of its spawned tasks.
    // And some scanning statistics may be tried to be added to the [scanningHistory],
    // leading to ConcurrentModificationException in the statistics' processor.
    val allTasksFinished = Ref.create(false)
    val sharedExplanationLogger = IndexingReasonExplanationLogger()
    val tasks = providers.map { provider: IndexableFilesIterator ->
      val scanningStatistics = ScanningStatistics(provider.debugName)
      scanningStatistics.setProviderRoots(provider, project)
      val origin = provider.origin
      val fileScannerVisitors = sessions.mapNotNull { s: ScanSession -> s.createVisitor(origin) }

      val thisProviderDeduplicateFilter =
        IndexableFilesDeduplicateFilter.createDelegatingTo(indexableFilesDeduplicateFilter)

      ProgressManager.checkCanceled() // give a chance to suspend indexing
      Runnable {
        val providerScanningStartTime = System.nanoTime()
        try {
          project.getService(PerProjectIndexingQueue::class.java)
            .getSink(provider, projectScanningHistory.scanningSessionId).use { perProviderSink ->
              progressReporter.getSubTaskReporter().use { subTaskReporter ->
                subTaskReporter.setText(provider.rootsScanningProgressText)
                val rootsAndFiles: MutableList<Pair<VirtualFile?, List<VirtualFile>>> = ArrayList()
                val singleProviderIteratorFactory = Function<VirtualFile?, ContentIterator> { root: VirtualFile? ->
                  val files: MutableList<VirtualFile> = ArrayList(1024)
                  rootsAndFiles.add(Pair<VirtualFile?, List<VirtualFile>>(root, files))
                  ContentIterator { fileOrDir: VirtualFile ->
                    // we apply scanners here, because scanners may mark directory as excluded, and we should skip excluded subtrees
                    // (e.g., JSDetectingProjectFileScanner.startSession will exclude "node_modules" directories during scanning)
                    PushedFilePropertiesUpdaterImpl.applyScannersToFile(fileOrDir, fileScannerVisitors)
                    files.add(fileOrDir)
                  }
                }

                scanningStatistics.startVfsIterationAndScanningApplication()
                provider.iterateFilesInRoots(project, singleProviderIteratorFactory, thisProviderDeduplicateFilter)
                scanningStatistics.tryFinishVfsIterationAndScanningApplication()

                scanningStatistics.startFileChecking()
                for ((first, second) in rootsAndFiles) {
                  val finder = UnindexedFilesFinder(project, sharedExplanationLogger, myIndex, forceReindexingTrigger,
                                                    first, scanningRequest, myFilterHandler)
                  val rootIterator = SingleProviderIterator(project, indicator, provider, finder,
                                                            scanningStatistics, perProviderSink)
                  if (!rootIterator.mayBeUsed()) {
                    LOG.warn("Iterator based on $provider can't be used.")
                    continue
                  }
                  second.forEach(
                    Consumer { it: VirtualFile? ->
                      rootIterator.processFile(
                        it!!)
                    })
                }
                scanningStatistics.tryFinishFilesChecking()
                perProviderSink.commit()
              }
            }
        }
        catch (pce: ProcessCanceledException) {
          scanningRequest.markUnsuccessful()
          throw pce
        }
        catch (e: Exception) {
          scanningRequest.markUnsuccessful()
          // CollectingIterator should skip failing files by itself. But if provider.iterateFiles cannot iterate files and throws exception,
          // we want to ignore the whole origin and let other origins complete normally.
          LOG.error("""
  Error while scanning files of ${provider.debugName}
  To reindex files under this origin IDEA has to be restarted
  """.trimIndent(), e)
        }
        finally {
          scanningStatistics.tryFinishVfsIterationAndScanningApplication()
          scanningStatistics.tryFinishFilesChecking()
          scanningStatistics.totalOneThreadTimeWithPauses = System.nanoTime() - providerScanningStartTime
          scanningStatistics.numberOfSkippedFiles = thisProviderDeduplicateFilter.numberOfSkippedFiles
          synchronized(allTasksFinished) {
            if (!allTasksFinished.get()) {
              projectScanningHistory.addScanningStatistics(scanningStatistics)
            }
          }
        }
      }
    }
    LOG.info("Scanning of " + myProject.name + " uses " + UnindexedFilesUpdater.getNumberOfScanningThreads() + " scanning threads")
    try {
      PushedFilePropertiesUpdaterImpl.invokeConcurrentlyIfPossible(tasks)
    }
    finally {
      synchronized(allTasksFinished) {
        allTasksFinished.set(true)
        projectIndexingDependenciesService.completeToken(scanningRequest, isFullIndexUpdate())
      }
    }
  }

  override fun perform(indicator: ProgressIndicator) {
    perform(object : CheckPauseOnlyProgressIndicator {
      override fun onPausedStateChanged(action: Consumer<Boolean>) {}

      override fun freezeIfPaused() {}
    }, IndexingProgressReporter(indicator))
  }

  fun perform(indicator: CheckPauseOnlyProgressIndicator, progressReporter: IndexingProgressReporter) {
    LOG.assertTrue(myProject.getUserData(INDEX_UPDATE_IN_PROGRESS) != true, "Scanning is already in progress")
    myProject.putUserData(INDEX_UPDATE_IN_PROGRESS, true)
    myFilterHandler.scanningStarted(myProject, isFullIndexUpdate())
    try {
      val history = performScanningAndIndexing(indicator, progressReporter)
      futureScanningHistory.set(history)
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

  protected fun performScanningAndIndexing(indicator: CheckPauseOnlyProgressIndicator,
                                           progressReporter: IndexingProgressReporter): ProjectScanningHistory {
    if (ApplicationManager.getApplication().isUnitTestMode && DELAY_IN_TESTS_MS > 0) {
      // This is to ease discovering races, when a test needs indexes, but forgot to wait for smart mode (see IndexingTestUtil).
      // It's better to install assert into FileBasedIndexImpl about access without explicit "wait" or "nowait" action, but this is
      // not feasible at the moment. At the moment, just keep things easy. Most tests cannot tolerate DELAY_IN_TESTS_MS delay.
      // Test will run a bit slower, but behavior will be more stable
      LockSupport.parkNanos(DELAY_IN_TESTS_MS * 1000000L)
    }
    val scanningHistory = ProjectScanningHistoryImpl(myProject, myIndexingReason, myScanningType)
    myIndex.loadIndexes()
    myIndex.registeredIndexes.waitUntilAllIndicesAreInitialized() // wait until stale ids are deleted
    if (myStartCondition != null) { // wait until indexes for dirty files are cleared
      ProgressIndicatorUtils.awaitWithCheckCanceled(myStartCondition)
    }
    // Not sure that ensureUpToDate is really needed, but it wouldn't hurt to clear up queue not from EDT
    // It was added in this commit: 'Process vfs events asynchronously (IDEA-109525), first cut Maxim.Mossienko 13.11.16, 14:15'
    myIndex.changedFilesCollector.ensureUpToDate()
    getInstance().onScanningStarted(scanningHistory)
    val markRef = Ref<StatusMark>()
    try {
      startDumbModeBeginningTracking(myProject, scanningHistory)
      (GistManager.getInstance() as GistManagerImpl).runWithMergingDependentCacheInvalidations {
        scanAndUpdateUnindexedFiles(scanningHistory, indicator, progressReporter, markRef)
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
      finishDumbModeBeginningTracking(myProject)
      if (DependenciesIndexedStatusService.shouldBeUsed() && IndexInfrastructure.hasIndices()) {
        DependenciesIndexedStatusService.getInstance(myProject)
          .indexingFinished(!scanningHistory.times.wasInterrupted, markRef.get())
      }
      getInstance().onScanningFinished(scanningHistory)
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
    if (isDumb(myProject) && Registry.`is`("scanning.waits.for.non.dumb.mode", true)) {
      object : DumbModeTask() {
        override fun performInDumbMode(indicator: ProgressIndicator) {
          getInstance(myProject).submitTask(this@UnindexedFilesScanner)
        }
      }.queue(myProject)
    }
    else {
      getInstance(myProject).submitTask(this)
    }
    return futureScanningHistory
  }

  override fun dispose() {
    if (!myProject.isDisposed) {
      val serviceIfCreated = myProject.getServiceIfCreated(
        ProjectIndexingDependenciesService::class.java)
      serviceIfCreated?.completeToken(myFutureScanningRequestToken)
    }
  }

  @TestOnly
  fun setFlushQueueAfterScanning(flushQueueAfterScanning: Boolean) {
    this.flushQueueAfterScanning = flushQueueAfterScanning
  }

  companion object {
    private val DELAY_IN_TESTS_MS = SystemProperties.getIntProperty("scanning.delay.before.start.in.tests.ms", 0)

    @JvmField
    val LOG: Logger = Logger.getInstance(UnindexedFilesScanner::class.java)

    @JvmField
    @VisibleForTesting
    @Volatile
    var ourTestMode: TestMode? = null

    private val CONTENT_SCANNED = Key.create<Boolean>("CONTENT_SCANNED")
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

    private fun markStage(scanningHistory: ProjectScanningHistoryImpl, scanningStage: ProjectScanningHistoryImpl.Stage, isStart: Boolean) {
      ProgressManager.checkCanceled()
      val scanningStageTime = Instant.now()
      if (isStart) {
        scanningHistory.startStage(scanningStage, scanningStageTime)
      }
      else {
        scanningHistory.stopStage(scanningStage, scanningStageTime)
      }
      ProgressManager.checkCanceled()
    }

    private fun getLogScanningCompletedStageMessage(scanningHistory: ProjectScanningHistory): String {
      val statistics = scanningHistory.scanningStatistics
      val numberOfScannedFiles = statistics.map(JsonScanningStatistics::numberOfScannedFiles).sum()
      val numberOfFilesForIndexing = statistics.map(JsonScanningStatistics::numberOfFilesForIndexing).sum()
      return "Scanning completed for " + scanningHistory.project.name + '.' +
             " Number of scanned files: " + numberOfScannedFiles + "; number of files for indexing: " + numberOfFilesForIndexing
    }

    @JvmStatic
    fun isIndexUpdateInProgress(project: Project): Boolean {
      return project.getUserData(INDEX_UPDATE_IN_PROGRESS) == true
    }

    @JvmStatic
    fun isProjectContentFullyScanned(project: Project): Boolean {
      return true == project.getUserData(CONTENT_SCANNED)
    }

    @JvmStatic
    fun isFirstProjectScanningRequested(project: Project): Boolean {
      return project.getUserData(FIRST_SCANNING_REQUESTED) != null
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
