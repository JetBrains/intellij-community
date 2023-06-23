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
import com.intellij.openapi.progress.impl.ProgressSuspender;
import com.intellij.openapi.project.DumbModeProgressTitle;
import com.intellij.openapi.project.FilesScanningTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.UnindexedFilesScannerExecutor;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater;
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdaterImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.TestModeFlags;
import com.intellij.util.BooleanFunction;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.gist.GistManager;
import com.intellij.util.gist.GistManagerImpl;
import com.intellij.util.indexing.PerProjectIndexingQueue.PerProviderSink;
import com.intellij.util.indexing.dependenciesCache.DependenciesIndexedStatusService;
import com.intellij.util.indexing.dependenciesCache.DependenciesIndexedStatusService.StatusMark;
import com.intellij.util.indexing.diagnostic.*;
import com.intellij.util.indexing.diagnostic.dto.JsonScanningStatistics;
import com.intellij.util.indexing.roots.IndexableFileScanner;
import com.intellij.util.indexing.roots.IndexableFilesDeduplicateFilter;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin;
import com.intellij.util.indexing.roots.kind.SdkOrigin;
import com.intellij.util.progress.ConcurrentTasksProgressManager;
import com.intellij.util.progress.SubTaskProgressIndicator;
import kotlin.Pair;
import org.jetbrains.annotations.*;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.intellij.openapi.project.UnindexedFilesScannerExecutor.shouldScanInSmartMode;

@ApiStatus.Internal
public class UnindexedFilesScanner implements FilesScanningTask {
  @VisibleForTesting
  public static final Key<Boolean> INDEX_PROJECT_WITH_MANY_UPDATERS_TEST_KEY = new Key<>("INDEX_PROJECT_WITH_MANY_UPDATERS_TEST_KEY");

  static final Logger LOG = Logger.getInstance(UnindexedFilesScanner.class);  // only for test debugging purpose

  public enum TestMode {
    PUSHING, PUSHING_AND_SCANNING
  }

  @SuppressWarnings("StaticNonFinalField") @VisibleForTesting
  public static volatile TestMode ourTestMode;

  private static final @NotNull Key<Boolean> CONTENT_SCANNED = Key.create("CONTENT_SCANNED");
  private static final @NotNull Key<Boolean> INDEX_UPDATE_IN_PROGRESS = Key.create("INDEX_UPDATE_IN_PROGRESS");
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
  public boolean isFullIndexUpdate() {
    return myPredefinedIndexableFilesIterators == null;
  }

  @Override
  public void dispose() {

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

  public UnindexedFilesScanner(@NotNull Project project, @Nullable @NonNls String indexingReason) {
    this(project, false, false, null, null, indexingReason, ScanningType.FULL);
  }

  private void scan(@NotNull PerformanceWatcher.Snapshot snapshot,
                    @NotNull ProjectIndexingHistoryImpl projectIndexingHistory,
                    @NotNull ProjectScanningHistoryImpl scanningHistory,
                    @NotNull ProgressIndicator indicator,
                    @NotNull Ref<? super StatusMark> markRef) {
    markStage(projectIndexingHistory, ProjectIndexingHistoryImpl.Stage.PushProperties,
              scanningHistory, ProjectScanningHistoryImpl.ScanningStage.DelayedPushProperties, true);
    try {
      if (myPusher instanceof PushedFilePropertiesUpdaterImpl) {
        ((PushedFilePropertiesUpdaterImpl)myPusher).performDelayedPushTasks();
      }
    }
    finally {
      markStage(projectIndexingHistory, ProjectIndexingHistoryImpl.Stage.PushProperties,
                scanningHistory, ProjectScanningHistoryImpl.ScanningStage.DelayedPushProperties, false);
    }
    LOG.info(snapshot.getLogResponsivenessSinceCreationMessage("Performing delayed pushing properties tasks for " + myProject.getName()));

    snapshot = PerformanceWatcher.takeSnapshot();

    if (isFullIndexUpdate()) {
      myIndex.clearIndicesIfNecessary();
    }

    List<IndexableFilesIterator> orderedProviders;
    markStage(projectIndexingHistory, ProjectIndexingHistoryImpl.Stage.CreatingIterators,
              scanningHistory, ProjectScanningHistoryImpl.ScanningStage.CreatingIterators, true);
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
      markStage(projectIndexingHistory, ProjectIndexingHistoryImpl.Stage.CreatingIterators,
                scanningHistory, ProjectScanningHistoryImpl.ScanningStage.CreatingIterators, false);
    }

    markStage(projectIndexingHistory, ProjectIndexingHistoryImpl.Stage.Scanning,
              scanningHistory, ProjectScanningHistoryImpl.ScanningStage.CollectingIndexableFiles, true);
    try {
      collectIndexableFilesConcurrently(myProject, indicator, orderedProviders, projectIndexingHistory, scanningHistory);
      if (isFullIndexUpdate()) {
        myProject.putUserData(CONTENT_SCANNED, true);
      }
    }
    finally {
      markStage(projectIndexingHistory, ProjectIndexingHistoryImpl.Stage.Scanning,
                scanningHistory, ProjectScanningHistoryImpl.ScanningStage.CollectingIndexableFiles, false);
    }
    String scanningCompletedMessage = getLogScanningCompletedStageMessage(projectIndexingHistory);
    LOG.info(snapshot.getLogResponsivenessSinceCreationMessage(scanningCompletedMessage));
  }

  private static void markStage(@NotNull ProjectIndexingHistoryImpl projectIndexingHistory,
                                @NotNull ProjectIndexingHistoryImpl.Stage stage,
                                @NotNull ProjectScanningHistoryImpl scanningHistory,
                                @NotNull ProjectScanningHistoryImpl.ScanningStage scanningStage,
                                boolean isStart) {
    ProgressManager.checkCanceled();
    Instant scanningStageTime = Instant.now();
    if (isStart) {
      projectIndexingHistory.startStage(stage, scanningStageTime);
      scanningHistory.startStage(scanningStage, scanningStageTime);
    }
    else {
      projectIndexingHistory.stopStage(stage, scanningStageTime);
      scanningHistory.stopStage(scanningStage, scanningStageTime);
    }
    ProgressManager.checkCanceled();
  }

  private void scanAndUpdateUnindexedFiles(@NotNull ProjectIndexingHistoryImpl projectIndexingHistory,
                                           @NotNull ProjectScanningHistoryImpl scanningHistory,
                                           @NotNull ProgressIndicator indicator,
                                           @NotNull Ref<? super StatusMark> markRef) {
    if (!IndexInfrastructure.hasIndices()) {
      return;
    }
    LOG.info("Started scanning for indexing of " + myProject.getName() + ". Reason: " + myIndexingReason);

    ProgressSuspender suspender = ProgressSuspender.getSuspender(indicator);
    if (suspender != null) {
      ApplicationManager.getApplication().getMessageBus().connect(this)
        .subscribe(ProgressSuspender.TOPIC, new ProgressSuspender.SuspenderListener() {
          @Override
          public void suspendedStatusChanged(@NotNull ProgressSuspender changedSuspender) {
            if (suspender == changedSuspender) {
              Instant now = Instant.now();
              if (suspender.isSuspended()) {
                projectIndexingHistory.suspendStages(now);
                scanningHistory.suspendStages(now);
              }
              else {
                projectIndexingHistory.stopSuspendingStages(now);
                scanningHistory.stopSuspendingStages(now);
              }
            }
          }
        });
    }

    if (myStartSuspended) {
      if (suspender == null) {
        throw new IllegalStateException("Indexing progress indicator must be suspendable!");
      }
      if (!suspender.isSuspended()) {
        suspender.suspendProcess(IndexingBundle.message("progress.indexing.started.as.suspended"));
      }
    }

    indicator.setIndeterminate(true);
    indicator.setText(IndexingBundle.message("progress.indexing.scanning"));

    PerformanceWatcher.Snapshot snapshot = PerformanceWatcher.takeSnapshot();
    Disposable scanningLifetime = Disposer.newDisposable();
    try {
      if (!shouldScanInSmartMode()) {
        DumbModeProgressTitle.getInstance(myProject)
          .attachProgressTitleText(IndexingBundle.message("progress.indexing.scanning.title"), scanningLifetime);
      }
      scan(snapshot, projectIndexingHistory, scanningHistory, indicator, markRef);
    }
    finally {
      Disposer.dispose(scanningLifetime);
    }

    boolean skipInitialRefresh = skipInitialRefresh();
    boolean isUnitTestMode = ApplicationManager.getApplication().isUnitTestMode();
    if (myOnProjectOpen && !isUnitTestMode && !skipInitialRefresh) {
      // the full VFS refresh makes sense only after it's loaded, i.e. after scanning files to index is finished
      InitialRefreshKt.scheduleInitialVfsRefresh(myProject, LOG);
    }

    if (flushQueueAfterScanning) {
      flushPerProjectIndexingQueue(projectIndexingHistory, indicator);
    }
  }

  private void flushPerProjectIndexingQueue(@NotNull ProjectIndexingHistoryImpl projectIndexingHistory, @NotNull ProgressIndicator indicator) {
    if (shouldScanInSmartMode()) {
      // Switch to dumb mode and index
      myProject.getService(PerProjectIndexingQueue.class).flushNow(myIndexingReason);
    }
    else {
      // Already in dumb mode. Just invoke indexer
      myProject.getService(PerProjectIndexingQueue.class).flushNowSync(projectIndexingHistory, indicator);
    }
  }

  private static @NotNull String getLogScanningCompletedStageMessage(@NotNull ProjectIndexingHistoryImpl projectIndexingHistory) {
    List<JsonScanningStatistics> statistics = projectIndexingHistory.getScanningStatistics();
    int numberOfScannedFiles = statistics.stream().mapToInt(JsonScanningStatistics::getNumberOfScannedFiles).sum();
    int numberOfFilesForIndexing = statistics.stream().mapToInt(JsonScanningStatistics::getNumberOfFilesForIndexing).sum();
    return "Scanning completed for " +
           projectIndexingHistory.getProject().getName() +
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

  protected @Nullable BooleanFunction<IndexedFile> getForceReindexingTrigger() {
    return null;
  }

  private void collectIndexableFilesConcurrently(
    @NotNull Project project,
    @NotNull ProgressIndicator indicator,
    @NotNull List<? extends IndexableFilesIterator> providers,
    @NotNull ProjectIndexingHistoryImpl projectIndexingHistory,
    @NotNull ProjectScanningHistoryImpl projectScanningHistory) {
    if (providers.isEmpty()) {
      return;
    }
    List<IndexableFileScanner.ScanSession> sessions =
      ContainerUtil.map(IndexableFileScanner.EP_NAME.getExtensionList(), scanner -> scanner.startSession(project));

    IndexableFilesDeduplicateFilter indexableFilesDeduplicateFilter = IndexableFilesDeduplicateFilter.create();

    indicator.setText(IndexingBundle.message("progress.indexing.scanning"));
    indicator.setIndeterminate(false);
    indicator.setFraction(0);

    ConcurrentTasksProgressManager concurrentTasksProgressManager = new ConcurrentTasksProgressManager(indicator, providers.size());

    // Workaround for concurrent modification of the [projectIndexingHistory].
    // PushedFilePropertiesUpdaterImpl.invokeConcurrentlyIfPossible may finish earlier than all its spawned tasks have completed.
    // And some scanning statistics may be tried to be added to the [projectIndexingHistory],
    // leading to ConcurrentModificationException in the statistics' processor.
    Ref<Boolean> allTasksFinished = Ref.create(false);
    final IndexingReasonExplanationLogger sharedExplanationLogger = new IndexingReasonExplanationLogger();
    List<Runnable> tasks = ContainerUtil.map(providers, provider -> {
      SubTaskProgressIndicator subTaskIndicator = concurrentTasksProgressManager.createSubTaskIndicator(1);
      ScanningStatistics scanningStatistics = new ScanningStatistics(provider.getDebugName());
      scanningStatistics.setProviderRoots(provider, project);
      IndexableSetOrigin origin = provider.getOrigin();
      List<IndexableFileScanner.@NotNull IndexableFileVisitor> fileScannerVisitors =
        ContainerUtil.mapNotNull(sessions, s -> s.createVisitor(origin));

      IndexableFilesDeduplicateFilter thisProviderDeduplicateFilter =
        IndexableFilesDeduplicateFilter.createDelegatingTo(indexableFilesDeduplicateFilter);

      ProgressManager.checkCanceled(); // give a chance to suspend indexing

      return () -> {
        subTaskIndicator.setText(provider.getRootsScanningProgressText());
        try (PerProviderSink perProviderSink = project.getService(PerProjectIndexingQueue.class)
          .getSink(provider, projectScanningHistory.getScanningSessionId())) {
          Function<@Nullable VirtualFile, ContentIterator> singleProviderIteratorFactory = root -> {
            UnindexedFilesFinder finder = new UnindexedFilesFinder(project, sharedExplanationLogger, myIndex, getForceReindexingTrigger(),
                                                                   root);
            return new SingleProviderIterator(project, subTaskIndicator, provider, fileScannerVisitors,
                                              finder, scanningStatistics, perProviderSink);
          };
          provider.iterateFilesInRoots(project, singleProviderIteratorFactory, thisProviderDeduplicateFilter);
          perProviderSink.commit();
        }
        catch (ProcessCanceledException pce) {
          throw pce;
        }
        catch (Exception e) {
          // CollectingIterator should skip failing files by itself. But if provider.iterateFiles cannot iterate files and throws exception,
          // we want to ignore whole origin and let other origins to complete normally.
          LOG.error("Error while scanning files of " + provider.getDebugName() + "\n" +
                    "To reindex files under this origin IDEA has to be restarted", e);
        }
        finally {
          scanningStatistics.setNumberOfSkippedFiles(thisProviderDeduplicateFilter.getNumberOfSkippedFiles());
          synchronized (allTasksFinished) {
            if (!allTasksFinished.get()) {
              projectIndexingHistory.addScanningStatistics(scanningStatistics);
              projectScanningHistory.addScanningStatistics(scanningStatistics);
            }
          }
          subTaskIndicator.finished();
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
  public void perform(@NotNull ProgressIndicator indicator) {
    LOG.assertTrue(myProject.getUserData(INDEX_UPDATE_IN_PROGRESS) != Boolean.TRUE, "Scanning is already in progress");
    myProject.putUserData(INDEX_UPDATE_IN_PROGRESS, true);
    try {
      performScanningAndIndexing(indicator);
    } finally {
      myProject.putUserData(INDEX_UPDATE_IN_PROGRESS, false);
    }
  }

  protected @NotNull ProjectIndexingHistoryImpl performScanningAndIndexing(@NotNull ProgressIndicator indicator) {
    ProjectIndexingHistoryImpl projectIndexingHistory =
      new ProjectIndexingHistoryImpl(myProject, myIndexingReason, myScanningType);
    ProjectScanningHistoryImpl scanningHistory = new ProjectScanningHistoryImpl(myProject, myIndexingReason, myScanningType);
    myIndex.loadIndexes();
    myIndex.filesUpdateStarted(myProject, isFullIndexUpdate());
    IndexDiagnosticDumper.getInstance().onIndexingStarted(projectIndexingHistory);
    IndexDiagnosticDumper.getInstance().onScanningStarted(scanningHistory);
    Ref<StatusMark> markRef = new Ref<>();
    try {
      ProjectScanningHistoryImpl.Companion.startDumbModeBeginningTracking(myProject, scanningHistory, projectIndexingHistory);
      ((GistManagerImpl)GistManager.getInstance()).
        runWithMergingDependentCacheInvalidations(() -> scanAndUpdateUnindexedFiles(projectIndexingHistory, scanningHistory,
                                                                                    indicator, markRef));
    }
    catch (Throwable e) {
      projectIndexingHistory.setWasInterrupted(true);
      scanningHistory.setWasInterrupted();
      if (e instanceof ControlFlowException) {
        LOG.info("Cancelled indexing of " + myProject.getName());
      }
      throw e;
    }
    finally {
      ProjectScanningHistoryImpl.Companion.finishDumbModeBeginningTracking(myProject);
      myIndex.filesUpdateFinished(myProject);
      projectIndexingHistory.finishTotalUpdatingTime();
      if (DependenciesIndexedStatusService.shouldBeUsed() && IndexInfrastructure.hasIndices()) {
        DependenciesIndexedStatusService.getInstance(myProject)
          .indexingFinished(!projectIndexingHistory.getTimes().getWasInterrupted(), markRef.get());
      }
      IndexDiagnosticDumper.getInstance().onIndexingFinished(projectIndexingHistory);
      IndexDiagnosticDumper.getInstance().onScanningFinished(scanningHistory);
    }
    return projectIndexingHistory;
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
    ((FileBasedIndexImpl)FileBasedIndex.getInstance()).loadIndexes();
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
      new UnindexedFilesScanner(project, startSuspended, true, null, null, indexingReason, ScanningType.FULL_ON_PROJECT_OPEN).
        queue(project);
    }
  }

  void queue(@NotNull Project project) {
    project.getService(UnindexedFilesScannerExecutor.class).submitTask(this);
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
