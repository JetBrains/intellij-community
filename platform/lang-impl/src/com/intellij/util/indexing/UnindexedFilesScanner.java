// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.*;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater;
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdaterImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.UserDataHolderEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.TestModeFlags;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.gist.GistManager;
import com.intellij.util.gist.GistManagerImpl;
import com.intellij.util.indexing.FilesScanningTaskBase.IndexingProgressReporter.IndexingSubTaskProgressReporter;
import com.intellij.util.indexing.PerProjectIndexingQueue.PerProviderSink;
import com.intellij.util.indexing.dependencies.IndexingRequestToken;
import com.intellij.util.indexing.dependencies.ProjectIndexingDependenciesService;
import com.intellij.util.indexing.dependenciesCache.DependenciesIndexedStatusService;
import com.intellij.util.indexing.dependenciesCache.DependenciesIndexedStatusService.StatusMark;
import com.intellij.util.indexing.diagnostic.*;
import com.intellij.util.indexing.diagnostic.dto.JsonScanningStatistics;
import com.intellij.util.indexing.roots.IndexableFileScanner;
import com.intellij.util.indexing.roots.IndexableFilesDeduplicateFilter;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin;
import com.intellij.util.indexing.roots.kind.SdkOrigin;
import kotlin.Pair;
import org.jetbrains.annotations.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.intellij.openapi.project.UnindexedFilesScannerExecutor.shouldScanInSmartMode;

@ApiStatus.Internal
public class UnindexedFilesScanner extends FilesScanningTaskBase {
  @VisibleForTesting
  public static final Key<Boolean> INDEX_PROJECT_WITH_MANY_UPDATERS_TEST_KEY = new Key<>("INDEX_PROJECT_WITH_MANY_UPDATERS_TEST_KEY");

  static final Logger LOG = Logger.getInstance(UnindexedFilesScanner.class);

  public enum TestMode {
    PUSHING, PUSHING_AND_SCANNING
  }

  private enum FirstScanningState {
    REQUESTED, PERFORMED
  }

  @SuppressWarnings("StaticNonFinalField") @VisibleForTesting
  public static volatile TestMode ourTestMode;

  private static final @NotNull Key<Boolean> CONTENT_SCANNED = Key.create("CONTENT_SCANNED");
  private static final @NotNull Key<Boolean> INDEX_UPDATE_IN_PROGRESS = Key.create("INDEX_UPDATE_IN_PROGRESS");
  private static final @NotNull Key<FirstScanningState> FIRST_SCANNING_REQUESTED = Key.create("FIRST_SCANNING_REQUESTED");
  private final FileBasedIndexImpl myIndex = (FileBasedIndexImpl)FileBasedIndex.getInstance();
  protected final Project myProject;
  private final boolean myStartSuspended;
  private final boolean myOnProjectOpen;
  private final @NotNull @NonNls String myIndexingReason;
  private final @NotNull ScanningType myScanningType;
  private final PushedFilePropertiesUpdater myPusher;
  private final @Nullable StatusMark myProvidedStatusMark;
  private final @Nullable List<IndexableFilesIterator> myPredefinedIndexableFilesIterators;
  private boolean flushQueueAfterScanning = true;


  public UnindexedFilesScanner(@NotNull Project project,
                               boolean startSuspended,
                               boolean onProjectOpen,
                               @Nullable List<IndexableFilesIterator> predefinedIndexableFilesIterators,
                               @Nullable StatusMark mark,
                               @Nullable @NonNls String indexingReason,
                               @NotNull ScanningType scanningType) {
    super(project);
    myProject = project;
    myStartSuspended = startSuspended;
    myOnProjectOpen = onProjectOpen;
    myIndexingReason = (indexingReason != null) ? indexingReason : "<unknown>";
    myScanningType = scanningType;
    myPusher = PushedFilePropertiesUpdater.getInstance(myProject);
    myProvidedStatusMark = predefinedIndexableFilesIterators == null ? null : mark;
    myPredefinedIndexableFilesIterators = predefinedIndexableFilesIterators;
    LOG.assertTrue(myPredefinedIndexableFilesIterators == null || !myPredefinedIndexableFilesIterators.isEmpty());

    if (isFullIndexUpdate()) {
      myProject.putUserData(CONTENT_SCANNED, null);
    }
  }

  @Override
  protected boolean shouldHideProgressInSmartMode() {
    return super.shouldHideProgressInSmartMode() || myProject.getUserData(FIRST_SCANNING_REQUESTED) == FirstScanningState.REQUESTED;
  }

  @Override
  public boolean isFullIndexUpdate() {
    return myPredefinedIndexableFilesIterators == null;
  }

  @Override
  public @Nullable UnindexedFilesScanner tryMergeWith(@NotNull FilesScanningTask _oldTask) {
    LOG.assertTrue(_oldTask.getClass() == getClass());
    UnindexedFilesScanner oldTask = (UnindexedFilesScanner)_oldTask;

    LOG.assertTrue(myProject.equals(oldTask.myProject));
    String reason;
    if (oldTask.isFullIndexUpdate()) {
      reason = oldTask.myIndexingReason;
    }
    else if (isFullIndexUpdate()) {
      reason = myIndexingReason;
    }
    else {
      reason = "Merged " + StringUtil.trimStart(myIndexingReason, "Merged ") +
               " with " + StringUtil.trimStart(oldTask.myIndexingReason, "Merged ");
    }
    LOG.debug("Merged " + this + " task");
    return new UnindexedFilesScanner(
      myProject,
      myStartSuspended,
      false,
      mergeIterators(myPredefinedIndexableFilesIterators, oldTask.myPredefinedIndexableFilesIterators),
      StatusMark.mergeStatus(myProvidedStatusMark, oldTask.myProvidedStatusMark),
      reason,
      ScanningType.Companion.merge(oldTask.myScanningType, oldTask.myScanningType)
    );
  }

  private static @Nullable List<IndexableFilesIterator> mergeIterators(@Nullable List<? extends IndexableFilesIterator> iterators,
                                                                       @Nullable List<? extends IndexableFilesIterator> otherIterators) {
    if (iterators == null || otherIterators == null) return null;
    Map<IndexableSetOrigin, IndexableFilesIterator> uniqueIterators = new LinkedHashMap<>();
    for (IndexableFilesIterator iterator : iterators) {
      uniqueIterators.putIfAbsent(iterator.getOrigin(), iterator);
    }
    for (IndexableFilesIterator iterator : otherIterators) {
      uniqueIterators.putIfAbsent(iterator.getOrigin(), iterator);
    }
    return new ArrayList<>(uniqueIterators.values());
  }

  private void scan(@NotNull PerformanceWatcher.Snapshot snapshot,
                    @NotNull ProjectScanningHistoryImpl scanningHistory,
                    @NotNull CheckCancelOnlyProgressIndicator indicator,
                    @NotNull IndexingProgressReporter progressReporter,
                    @NotNull Ref<? super StatusMark> markRef) {
    markStage(
      scanningHistory, ProjectScanningHistoryImpl.Stage.DelayedPushProperties, true);
    try {
      if (myPusher instanceof PushedFilePropertiesUpdaterImpl) {
        ((PushedFilePropertiesUpdaterImpl)myPusher).performDelayedPushTasks();
      }
    }
    finally {
      markStage(
        scanningHistory, ProjectScanningHistoryImpl.Stage.DelayedPushProperties, false);
    }
    LOG.info(snapshot.getLogResponsivenessSinceCreationMessage("Performing delayed pushing properties tasks for " + myProject.getName()));

    snapshot = PerformanceWatcher.takeSnapshot();

    if (isFullIndexUpdate()) {
      myIndex.clearIndicesIfNecessary();
    }

    List<IndexableFilesIterator> orderedProviders;
    markStage(
      scanningHistory, ProjectScanningHistoryImpl.Stage.CreatingIterators, true);
    try {
      if (myPredefinedIndexableFilesIterators == null) {
        Pair<@NotNull List<IndexableFilesIterator>, @NotNull StatusMark> pair = collectProviders(myProject, myIndex);
        orderedProviders = pair.getFirst();
        markRef.set(pair.getSecond());
      }
      else {
        orderedProviders = myPredefinedIndexableFilesIterators;
      }
    }
    finally {
      markStage(
        scanningHistory, ProjectScanningHistoryImpl.Stage.CreatingIterators, false);
    }

    markStage(
      scanningHistory, ProjectScanningHistoryImpl.Stage.CollectingIndexableFiles, true);
    try {
      collectIndexableFilesConcurrently(myProject, indicator, progressReporter, orderedProviders, scanningHistory);
      if (isFullIndexUpdate()) {
        myProject.putUserData(CONTENT_SCANNED, true);
      }
    }
    finally {
      markStage(
        scanningHistory, ProjectScanningHistoryImpl.Stage.CollectingIndexableFiles, false);
    }
    String scanningCompletedMessage = getLogScanningCompletedStageMessage(scanningHistory);
    LOG.info(snapshot.getLogResponsivenessSinceCreationMessage(scanningCompletedMessage));
  }

  private static void markStage(@NotNull ProjectScanningHistoryImpl scanningHistory,
                                @NotNull ProjectScanningHistoryImpl.Stage scanningStage,
                                boolean isStart) {
    ProgressManager.checkCanceled();
    Instant scanningStageTime = Instant.now();
    if (isStart) {
      scanningHistory.startStage(scanningStage, scanningStageTime);
    }
    else {
      scanningHistory.stopStage(scanningStage, scanningStageTime);
    }
    ProgressManager.checkCanceled();
  }

  private void scanAndUpdateUnindexedFiles(@NotNull ProjectScanningHistoryImpl scanningHistory,
                                           @NotNull CheckCancelOnlyProgressIndicator indicator,
                                           @NotNull IndexingProgressReporter progressReporter,
                                           @NotNull Ref<? super StatusMark> markRef) {
    try {
      if (!IndexInfrastructure.hasIndices()) {
        return;
      }
      scanUnindexedFiles(scanningHistory, indicator, progressReporter, markRef);
    }
    finally {
      // Scanning may throw exception (or error).
      // In this case, we should either clear or flush the indexing queue; otherwise, dumb mode will not end in the project.
      if (flushQueueAfterScanning) {
        flushPerProjectIndexingQueue(scanningHistory.getScanningReason(), indicator);
      }

      ((UserDataHolderEx)myProject).replace(FIRST_SCANNING_REQUESTED, FirstScanningState.REQUESTED, FirstScanningState.PERFORMED);
    }
  }

  private void scanUnindexedFiles(@NotNull ProjectScanningHistoryImpl scanningHistory,
                                  @NotNull CheckCancelOnlyProgressIndicator indicator,
                                  @NotNull IndexingProgressReporter progressReporter,
                                  @NotNull Ref<? super StatusMark> markRef) {
    LOG.info("Started scanning for indexing of " + myProject.getName() + ". Reason: " + myIndexingReason);

    indicator.onPausedStateChanged(paused -> {
      if (paused) {
        scanningHistory.suspendStages(Instant.now());
      }
      else {
        scanningHistory.stopSuspendingStages(Instant.now());
      }
    });

    if (myStartSuspended) {
      freezeUntilAllowed();
    }

    progressReporter.setIndeterminate(true);
    progressReporter.setText(IndexingBundle.message("progress.indexing.scanning"));

    PerformanceWatcher.Snapshot snapshot = PerformanceWatcher.takeSnapshot();
    Disposable scanningLifetime = Disposer.newDisposable();
    try {
      if (!shouldScanInSmartMode()) {
        DumbModeProgressTitle.getInstance(myProject)
          .attachProgressTitleText(IndexingBundle.message("progress.indexing.scanning.title"), scanningLifetime);
      }
      scan(snapshot, scanningHistory, indicator, progressReporter, markRef);
    }
    finally {
      Disposer.dispose(scanningLifetime);
    }

    boolean skipInitialRefresh = skipInitialRefresh();
    boolean isUnitTestMode = ApplicationManager.getApplication().isUnitTestMode();
    if (myOnProjectOpen && !isUnitTestMode && !skipInitialRefresh) {
      // the full VFS refresh makes sense only after it's loaded, i.e., after scanning files to index is finished
      InitialRefreshKt.scheduleInitialVfsRefresh(myProject, LOG);
    }
  }

  private void freezeUntilAllowed() {
    CountDownLatch latch = new CountDownLatch(1);
    ProgressManager.getInstance().run(
      new Task.Backgroundable(myProject, IndexingBundle.message("progress.indexing.started.as.suspended"), true) {
        @Override
        public void run(@NotNull ProgressIndicator indicator1) {
          try {
            //noinspection InfiniteLoopStatement
            while (true) {
              LockSupport.parkNanos(100_000);
              ProgressManager.checkCanceled();
            }
          }
          finally {
            latch.countDown();
          }
        }
      }
    );
    ProgressIndicatorUtils.awaitWithCheckCanceled(latch);
  }

  private void flushPerProjectIndexingQueue(@Nullable String indexingReason,
                                            @NotNull CheckCancelOnlyProgressIndicator indicator) {
    if (shouldScanInSmartMode()) {
      // Switch to dumb mode and index
      myProject.getService(PerProjectIndexingQueue.class).flushNow(myIndexingReason);
    }
    else {
      // Already in dumb mode. Just invoke indexer
      myProject.getService(PerProjectIndexingQueue.class).flushNowSync(indexingReason,
                                                                       indicator.originalIndicatorOnlyToFlushIndexingQueueSynchronously());
    }
  }

  private static @NotNull String getLogScanningCompletedStageMessage(@NotNull ProjectScanningHistory scanningHistory) {
    List<JsonScanningStatistics> statistics = scanningHistory.getScanningStatistics();
    int numberOfScannedFiles = statistics.stream().mapToInt(JsonScanningStatistics::getNumberOfScannedFiles).sum();
    int numberOfFilesForIndexing = statistics.stream().mapToInt(JsonScanningStatistics::getNumberOfFilesForIndexing).sum();
    return "Scanning completed for " +
           scanningHistory.getProject().getName() +
           ". Number of scanned files: " +
           numberOfScannedFiles +
           "; " +
           "Number of files for indexing: " +
           numberOfFilesForIndexing;
  }

  public static boolean isIndexUpdateInProgress(@NotNull Project project) {
    return project.getUserData(INDEX_UPDATE_IN_PROGRESS) == Boolean.TRUE;
  }

  public static boolean isProjectContentFullyScanned(@NotNull Project project) {
    return Boolean.TRUE.equals(project.getUserData(CONTENT_SCANNED));
  }

  public static boolean isFirstProjectScanningRequested(@NotNull Project project) {
    return project.getUserData(FIRST_SCANNING_REQUESTED) != null;
  }

  @NotNull
  private static Pair<@NotNull List<IndexableFilesIterator>, @Nullable StatusMark> collectProviders(@NotNull Project project,
                                                                                                    FileBasedIndexImpl index) {
    boolean cache = DependenciesIndexedStatusService.shouldBeUsed();
    List<IndexableFilesIterator> originalOrderedProviders;
    StatusMark mark = null;
    if (cache) {
      DependenciesIndexedStatusService.getInstance(project).startCollectingStatus();
    }
    try {
      originalOrderedProviders = index.getIndexableFilesProviders(project);
    }
    finally {
      if (cache) {
        mark = DependenciesIndexedStatusService.getInstance(project).finishCollectingStatus();
      }
    }

    List<IndexableFilesIterator> orderedProviders = new ArrayList<>();
    originalOrderedProviders.stream()
      .filter(p -> !(p.getOrigin() instanceof SdkOrigin))
      .collect(Collectors.toCollection(() -> orderedProviders));

    originalOrderedProviders.stream()
      .filter(p -> p.getOrigin() instanceof SdkOrigin)
      .collect(Collectors.toCollection(() -> orderedProviders));

    return new Pair<>(orderedProviders, mark);
  }

  protected @Nullable Predicate<IndexedFile> getForceReindexingTrigger() {
    return null;
  }

  private void collectIndexableFilesConcurrently(
    @NotNull Project project,
    @NotNull CheckCancelOnlyProgressIndicator indicator,
    @NotNull IndexingProgressReporter progressReporter,
    @NotNull List<? extends IndexableFilesIterator> providers,
    @NotNull ProjectScanningHistoryImpl projectScanningHistory) {
    if (providers.isEmpty()) {
      return;
    }
    IndexingRequestToken indexingRequest = myProject.getService(ProjectIndexingDependenciesService.class).getLatestIndexingRequestToken();
    List<IndexableFileScanner.ScanSession> sessions =
      ContainerUtil.map(IndexableFileScanner.EP_NAME.getExtensionList(), scanner -> scanner.startSession(project));

    IndexableFilesDeduplicateFilter indexableFilesDeduplicateFilter = IndexableFilesDeduplicateFilter.create();

    progressReporter.setText(IndexingBundle.message("progress.indexing.scanning"));
    progressReporter.setSubTasksCount(providers.size());

    // Workaround for concurrent modification of the [scanningHistory].
    // PushedFilePropertiesUpdaterImpl.invokeConcurrentlyIfPossible may finish earlier than some of its spawned tasks.
    // And some scanning statistics may be tried to be added to the [scanningHistory],
    // leading to ConcurrentModificationException in the statistics' processor.
    Ref<Boolean> allTasksFinished = Ref.create(false);
    final IndexingReasonExplanationLogger sharedExplanationLogger = new IndexingReasonExplanationLogger();
    List<Runnable> tasks = ContainerUtil.map(providers, provider -> {
      ScanningStatistics scanningStatistics = new ScanningStatistics(provider.getDebugName());
      scanningStatistics.setProviderRoots(provider, project);
      IndexableSetOrigin origin = provider.getOrigin();
      List<IndexableFileScanner.@NotNull IndexableFileVisitor> fileScannerVisitors =
        ContainerUtil.mapNotNull(sessions, s -> s.createVisitor(origin));

      IndexableFilesDeduplicateFilter thisProviderDeduplicateFilter =
        IndexableFilesDeduplicateFilter.createDelegatingTo(indexableFilesDeduplicateFilter);

      ProgressManager.checkCanceled(); // give a chance to suspend indexing

      return () -> {
        long providerScanningStartTime = System.nanoTime();
        try (
          PerProviderSink perProviderSink = project.getService(PerProjectIndexingQueue.class)
            .getSink(provider, projectScanningHistory.getScanningSessionId());
          IndexingSubTaskProgressReporter subTaskReporter = progressReporter.getSubTaskReporter()
        ) {
          subTaskReporter.setText(provider.getRootsScanningProgressText());
          List<Pair<VirtualFile, List<VirtualFile>>> rootsAndFiles = new ArrayList<>();
          Function<@Nullable VirtualFile, ContentIterator> singleProviderIteratorFactory = root -> {
            List<VirtualFile> files = new ArrayList<>(1024);
            rootsAndFiles.add(new Pair<>(root, files));
            return fileOrDir -> {
              // we apply scanners here, because scanners may mark directory as excluded, and we should skip excluded subtrees
              // (e.g., JSDetectingProjectFileScanner.startSession will exclude "node_modules" directories during scanning)
              PushedFilePropertiesUpdaterImpl.applyScannersToFile(fileOrDir, fileScannerVisitors);
              return files.add(fileOrDir);
            };
          };

          scanningStatistics.startVfsIterationAndScanningApplication();
          provider.iterateFilesInRoots(project, singleProviderIteratorFactory, thisProviderDeduplicateFilter);
          scanningStatistics.tryFinishVfsIterationAndScanningApplication();

          scanningStatistics.startFileChecking();
          for (Pair<VirtualFile, List<VirtualFile>> rootAndFiles : rootsAndFiles) {
            UnindexedFilesFinder finder = new UnindexedFilesFinder(project, sharedExplanationLogger, myIndex, getForceReindexingTrigger(),
                                                                   rootAndFiles.getFirst(), indexingRequest);
            var rootIterator = new SingleProviderIterator(project, indicator, provider, finder,
                                                          scanningStatistics, perProviderSink);
            rootAndFiles.getSecond().forEach(it -> rootIterator.processFile(it));
          }
          scanningStatistics.tryFinishFilesChecking();

          perProviderSink.commit();
        }
        catch (ProcessCanceledException pce) {
          throw pce;
        }
        catch (Exception e) {
          // CollectingIterator should skip failing files by itself. But if provider.iterateFiles cannot iterate files and throws exception,
          // we want to ignore the whole origin and let other origins complete normally.
          LOG.error("Error while scanning files of " + provider.getDebugName() + "\n" +
                    "To reindex files under this origin IDEA has to be restarted", e);
        }
        finally {
          scanningStatistics.tryFinishVfsIterationAndScanningApplication();
          scanningStatistics.tryFinishFilesChecking();
          scanningStatistics.setTotalOneThreadTimeWithPauses(System.nanoTime() - providerScanningStartTime);
          scanningStatistics.setNumberOfSkippedFiles(thisProviderDeduplicateFilter.getNumberOfSkippedFiles());
          synchronized (allTasksFinished) {
            if (!allTasksFinished.get()) {
              projectScanningHistory.addScanningStatistics(scanningStatistics);
            }
          }
        }
      };
    });
    LOG.info("Scanning of " + myProject.getName() + " uses " + UnindexedFilesUpdater.getNumberOfScanningThreads() + " scanning threads");
    try {
      PushedFilePropertiesUpdaterImpl.invokeConcurrentlyIfPossible(tasks);
    }
    finally {
      synchronized (allTasksFinished) {
        allTasksFinished.set(true);
      }
    }
  }

  @Override
  public void perform(@NotNull CheckCancelOnlyProgressIndicator indicator, @NotNull IndexingProgressReporter progressReporter) {
    LOG.assertTrue(myProject.getUserData(INDEX_UPDATE_IN_PROGRESS) != Boolean.TRUE, "Scanning is already in progress");
    myProject.putUserData(INDEX_UPDATE_IN_PROGRESS, true);
    try {
      performScanningAndIndexing(indicator, progressReporter);
    } finally {
      myProject.putUserData(INDEX_UPDATE_IN_PROGRESS, false);
    }
  }

  protected @NotNull ProjectScanningHistory performScanningAndIndexing(@NotNull CheckCancelOnlyProgressIndicator indicator,
                                                                       @NotNull IndexingProgressReporter progressReporter) {
    ProjectScanningHistoryImpl scanningHistory = new ProjectScanningHistoryImpl(myProject, myIndexingReason, myScanningType);
    myIndex.loadIndexes();
    myIndex.filesUpdateStarted(myProject, isFullIndexUpdate());
    IndexDiagnosticDumper.getInstance().onScanningStarted(scanningHistory);
    Ref<StatusMark> markRef = new Ref<>();
    try {
      ProjectScanningHistoryImpl.Companion.startDumbModeBeginningTracking(myProject, scanningHistory);
      ((GistManagerImpl)GistManager.getInstance()).
        runWithMergingDependentCacheInvalidations(() -> scanAndUpdateUnindexedFiles(scanningHistory,
                                                                                    indicator, progressReporter,
                                                                                    markRef));
    }
    catch (Throwable e) {
      scanningHistory.setWasInterrupted();
      if (e instanceof ControlFlowException) {
        LOG.info("Cancelled indexing of " + myProject.getName());
      }
      throw e;
    }
    finally {
      ProjectScanningHistoryImpl.Companion.finishDumbModeBeginningTracking(myProject);
      myIndex.filesUpdateFinished(myProject);
      if (DependenciesIndexedStatusService.shouldBeUsed() && IndexInfrastructure.hasIndices()) {
        DependenciesIndexedStatusService.getInstance(myProject)
          .indexingFinished(!scanningHistory.getTimes().getWasInterrupted(), markRef.get());
      }
      IndexDiagnosticDumper.getInstance().onScanningFinished(scanningHistory);
    }
    return scanningHistory;
  }

  @Override
  public String toString() {
    String partialInfo = myPredefinedIndexableFilesIterators != null
                         ? (", " + myPredefinedIndexableFilesIterators.size() + " iterators")
                         : "";
    return "UnindexedFilesScanner[" + myProject.getName() + partialInfo + "]";
  }

  private static boolean skipInitialRefresh() {
    return SystemProperties.getBooleanProperty("ij.indexes.skip.initial.refresh", false);
  }

  public static void scanAndIndexProjectAfterOpen(@NotNull Project project,
                                                  boolean startSuspended,
                                                  @Nullable @NonNls String indexingReason) {
    FileBasedIndex.getInstance().loadIndexes();
    ((UserDataHolderEx)project).putUserDataIfAbsent(FIRST_SCANNING_REQUESTED, FirstScanningState.REQUESTED);
    if (TestModeFlags.is(INDEX_PROJECT_WITH_MANY_UPDATERS_TEST_KEY)) {
      LOG.assertTrue(ApplicationManager.getApplication().isUnitTestMode());
      List<IndexableFilesIterator> iterators = collectProviders(project, (FileBasedIndexImpl)FileBasedIndex.getInstance()).getFirst();
      for (IndexableFilesIterator iterator : iterators) {
        new UnindexedFilesScanner(project, startSuspended, true, Collections.singletonList(iterator), null, indexingReason,
                                  ScanningType.FULL_ON_PROJECT_OPEN).queue(project);
      }
      project.putUserData(CONTENT_SCANNED, true);
    }
    else {
      new UnindexedFilesScanner(project, startSuspended, true, null, null, indexingReason, ScanningType.FULL_ON_PROJECT_OPEN)
        .queue(project);
    }
  }

  void queue(@NotNull Project project) {
    // Delay scanning tasks until after all the scheduled dumb tasks are finished.
    // For example, PythonLanguageLevelPusher.initExtra is invoked from RequiredForSmartModeActivity and may submit additional dumb tasks.
    // We want scanning start after all these "extra" dumb tasks are finished.
    // Note that project may become dumb/smart immediately after the check
    // If project becomes smart, in the worst case we'll trigger additional short dumb mode
    // If project becomes dumb, not a problem at all - we'll schedule scanning task out of dumb mode either way.
    if (DumbService.isDumb(project)) {
      new DumbModeTask() {
        @Override
        public void performInDumbMode(@NotNull ProgressIndicator indicator) {
          project.getService(UnindexedFilesScannerExecutor.class).submitTask(UnindexedFilesScanner.this);
        }
      }.queue(project);
    }
    else {
      project.getService(UnindexedFilesScannerExecutor.class).submitTask(this);
    }
  }

  @Nullable
  List<IndexableFilesIterator> getPredefinedIndexableFilesIterators() {
    return myPredefinedIndexableFilesIterators;
  }

  @TestOnly
  void setFlushQueueAfterScanning(boolean flushQueueAfterScanning) {
    this.flushQueueAfterScanning = flushQueueAfterScanning;
  }
}
