// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.progress.impl.ProgressSuspender;
import com.intellij.openapi.project.*;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.impl.FilePropertyPusher;
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater;
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdaterImpl;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.openapi.vfs.newvfs.VfsUsageCollector;
import com.intellij.testFramework.TestModeFlags;
import com.intellij.util.BooleanFunction;
import com.intellij.util.SmartList;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.gist.GistManager;
import com.intellij.util.gist.GistManagerImpl;
import com.intellij.util.indexing.dependenciesCache.DependenciesIndexedStatusService;
import com.intellij.util.indexing.dependenciesCache.DependenciesIndexedStatusService.StatusMark;
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper;
import com.intellij.util.indexing.diagnostic.ProjectIndexingHistoryImpl;
import com.intellij.util.indexing.diagnostic.ScanningStatistics;
import com.intellij.util.indexing.diagnostic.ScanningType;
import com.intellij.util.indexing.diagnostic.dto.JsonScanningStatistics;
import com.intellij.util.indexing.roots.IndexableFileScanner;
import com.intellij.util.indexing.roots.IndexableFilesDeduplicateFilter;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin;
import com.intellij.util.indexing.roots.kind.ModuleRootOrigin;
import com.intellij.util.indexing.roots.kind.SdkOrigin;
import com.intellij.util.progress.ConcurrentTasksProgressManager;
import com.intellij.util.progress.SubTaskProgressIndicator;
import kotlin.Pair;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.intellij.openapi.roots.impl.PushedFilePropertiesUpdaterImpl.getImmediateValuesEx;
import static com.intellij.openapi.roots.impl.PushedFilePropertiesUpdaterImpl.getModuleImmediateValues;

@ApiStatus.Internal
public class UnindexedFilesScanner implements MergeableQueueTask<UnindexedFilesScanner> {
  @VisibleForTesting
  public static final Key<Boolean> INDEX_PROJECT_WITH_MANY_UPDATERS_TEST_KEY = new Key<>("INDEX_PROJECT_WITH_MANY_UPDATERS_TEST_KEY");

  private static final Logger LOG = Logger.getInstance(UnindexedFilesScanner.class);  // only for test debugging purpose

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
  private final @NonNls String myIndexingReason;
  private final @NotNull ScanningType myScanningType;
  private final PushedFilePropertiesUpdater myPusher;
  private final @Nullable StatusMark myProvidedStatusMark;
  private final @Nullable List<IndexableFilesIterator> myPredefinedIndexableFilesIterators;

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
    myIndexingReason = indexingReason;
    myScanningType = scanningType;
    myPusher = PushedFilePropertiesUpdater.getInstance(myProject);
    myProvidedStatusMark = predefinedIndexableFilesIterators == null ? null : mark;
    myPredefinedIndexableFilesIterators = predefinedIndexableFilesIterators;
    LOG.assertTrue(myPredefinedIndexableFilesIterators == null || !myPredefinedIndexableFilesIterators.isEmpty());

    if (isFullIndexUpdate()) {
      myProject.putUserData(CONTENT_SCANNED, null);
    }
  }

  protected boolean isFullIndexUpdate() {
    return myPredefinedIndexableFilesIterators == null;
  }

  @Override
  public void dispose() {

  }

  @Override
  public @Nullable UnindexedFilesScanner tryMergeWith(@NotNull UnindexedFilesScanner oldTask) {
    if (oldTask.getClass() != getClass()) return null;
    if (!myProject.equals(oldTask.myProject)) return null;
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

  private @NotNull Map<IndexableFilesIterator, List<VirtualFile>> scan(@NotNull PerformanceWatcher.Snapshot snapshot,
                                                                       @NotNull ProjectIndexingHistoryImpl projectIndexingHistory,
                                                                       @NotNull ProgressIndicator indicator,
                                                                       @NotNull Ref<? super StatusMark> markRef) {
    projectIndexingHistory.startStage(ProjectIndexingHistoryImpl.Stage.PushProperties);
    try {
      if (myPusher instanceof PushedFilePropertiesUpdaterImpl) {
        ((PushedFilePropertiesUpdaterImpl)myPusher).performDelayedPushTasks();
      }
    }
    finally {
      projectIndexingHistory.stopStage(ProjectIndexingHistoryImpl.Stage.PushProperties);
    }
    LOG.info(snapshot.getLogResponsivenessSinceCreationMessage("Performing delayed pushing properties tasks for " + myProject.getName()));

    snapshot = PerformanceWatcher.takeSnapshot();

    if (isFullIndexUpdate()) {
      myIndex.clearIndicesIfNecessary();
    }

    List<IndexableFilesIterator> orderedProviders;
    Map<IndexableFilesIterator, List<VirtualFile>> providerToFiles;
    projectIndexingHistory.startStage(ProjectIndexingHistoryImpl.Stage.CreatingIterators);
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
      projectIndexingHistory.stopStage(ProjectIndexingHistoryImpl.Stage.CreatingIterators);
    }

    projectIndexingHistory.startStage(ProjectIndexingHistoryImpl.Stage.Scanning);
    try {
      providerToFiles = collectIndexableFilesConcurrently(myProject, indicator, orderedProviders, projectIndexingHistory);
      if (isFullIndexUpdate()) {
        myProject.putUserData(CONTENT_SCANNED, true);
      }
    }
    finally {
      projectIndexingHistory.stopStage(ProjectIndexingHistoryImpl.Stage.Scanning);
    }
    String scanningCompletedMessage = getLogScanningCompletedStageMessage(projectIndexingHistory);
    LOG.info(snapshot.getLogResponsivenessSinceCreationMessage(scanningCompletedMessage));
    return providerToFiles;
  }

  private void scanAndUpdateUnindexedFiles(@NotNull ProjectIndexingHistoryImpl projectIndexingHistory,
                                           @NotNull ProgressIndicator indicator,
                                           @NotNull Ref<? super StatusMark> markRef) {
    if (!IndexInfrastructure.hasIndices()) {
      return;
    }
    LOG.info("Started scanning for indexing of " + myProject.getName() + (myIndexingReason == null ? "" : ". Reason: " + myIndexingReason));

    ProgressSuspender suspender = ProgressSuspender.getSuspender(indicator);
    if (suspender != null) {
      ApplicationManager.getApplication().getMessageBus().connect(this)
        .subscribe(ProgressSuspender.TOPIC, projectIndexingHistory.getSuspendListener(suspender));
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
    Map<IndexableFilesIterator, List<VirtualFile>> providerToFiles;
    try {
      if (!shouldScanInSmartMode()) {
        DumbModeProgressTitle.getInstance(myProject)
          .attachProgressTitleText(IndexingBundle.message("progress.indexing.scanning.title"), scanningLifetime);
      }
      providerToFiles = scan(snapshot, projectIndexingHistory, indicator, markRef);
    }
    finally {
      Disposer.dispose(scanningLifetime);
    }

    boolean skipInitialRefresh = skipInitialRefresh();
    boolean isUnitTestMode = ApplicationManager.getApplication().isUnitTestMode();
    if (myOnProjectOpen && !isUnitTestMode && !skipInitialRefresh) {
      // the full VFS refresh makes sense only after it's loaded, i.e. after scanning files to index is finished
      scheduleInitialVfsRefresh();
    }

    int totalFiles = providerToFiles.values().stream().mapToInt(List::size).sum();
    if (totalFiles > 0) {
      UnindexedFilesIndexer indexer = new UnindexedFilesIndexer(myProject, providerToFiles);
      if (shouldScanInSmartMode()) {
        // Switch to dumb mode and index
        indexer.queue(myProject);
      }
      else {
        // Already in dumb mode. Just invoke indexer
        indexer.indexFiles(projectIndexingHistory, indicator);
      }
    } else {
      LOG.info("Finished for " + myProject.getName() + ". No files to index with loading content.");
    }
  }

  static boolean shouldScanInSmartMode() {
    return !DumbServiceImpl.isSynchronousTaskExecution() && Registry.is("scanning.in.smart.mode", false);
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

  private @NotNull Map<IndexableFilesIterator, List<VirtualFile>> collectIndexableFilesConcurrently(
    @NotNull Project project,
    @NotNull ProgressIndicator indicator,
    @NotNull List<? extends IndexableFilesIterator> providers,
    @NotNull ProjectIndexingHistoryImpl projectIndexingHistory
  ) {
    if (providers.isEmpty()) {
      return Collections.emptyMap();
    }
    List<IndexableFileScanner.ScanSession> sessions =
      ContainerUtil.map(IndexableFileScanner.EP_NAME.getExtensionList(), scanner -> scanner.startSession(project));
    UnindexedFilesFinder unindexedFileFinder = new UnindexedFilesFinder(project, myIndex, getForceReindexingTrigger());

    Map<IndexableFilesIterator, List<VirtualFile>> providerToFiles = new IdentityHashMap<>();
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
    List<Runnable> tasks = ContainerUtil.map(providers, provider -> {
      SubTaskProgressIndicator subTaskIndicator = concurrentTasksProgressManager.createSubTaskIndicator(1);
      List<VirtualFile> files = new ArrayList<>();
      ScanningStatistics scanningStatistics = new ScanningStatistics(provider.getDebugName());
      scanningStatistics.setProviderRoots(provider, project);
      providerToFiles.put(provider, files);
      IndexableSetOrigin origin = provider.getOrigin();
      List<IndexableFileScanner.@NotNull IndexableFileVisitor> fileScannerVisitors =
        ContainerUtil.mapNotNull(sessions, s -> s.createVisitor(origin));

      IndexableFilesDeduplicateFilter thisProviderDeduplicateFilter =
        IndexableFilesDeduplicateFilter.createDelegatingTo(indexableFilesDeduplicateFilter);

      List<FilePropertyPusher<?>> pushers;
      List<FilePropertyPusherEx<?>> pusherExs;
      Object[] moduleValues;
      if (origin instanceof ModuleRootOrigin && !((ModuleRootOrigin)origin).getModule().isDisposed()) {
        pushers = FilePropertyPusher.EP_NAME.getExtensionList();
        pusherExs = null;
        moduleValues = ReadAction.compute(() -> getModuleImmediateValues(pushers, ((ModuleRootOrigin)origin).getModule()));
      }
      else {
        pushers = null;
        List<FilePropertyPusherEx<?>> extendedPushers = new SmartList<>();
        for (FilePropertyPusher<?> pusher : FilePropertyPusher.EP_NAME.getExtensionList()) {
          if (pusher instanceof FilePropertyPusherEx && ((FilePropertyPusherEx<?>)pusher).acceptsOrigin(project, origin)) {
            extendedPushers.add((FilePropertyPusherEx<?>)pusher);
          }
        }
        if (extendedPushers.isEmpty()) {
          pusherExs = null;
          moduleValues = null;
        }
        else {
          pusherExs = extendedPushers;
          moduleValues = ReadAction.compute(() -> getImmediateValuesEx(extendedPushers, origin));
        }
      }

      ProgressManager.checkCanceled(); // give a chance to suspend indexing
      ContentIterator collectingIterator = fileOrDir -> {

        ProgressManager.checkCanceled(); // give a chance to suspend indexing
        if (subTaskIndicator.isCanceled()) {
          return false;
        }
        long scanningStart = System.nanoTime();
        PushedFilePropertiesUpdaterImpl.applyScannersToFile(fileOrDir, fileScannerVisitors);
        if (pushers != null && myPusher instanceof PushedFilePropertiesUpdaterImpl) {
          ((PushedFilePropertiesUpdaterImpl)myPusher).applyPushersToFile(fileOrDir, pushers, moduleValues);
        }
        else if (pusherExs != null && myPusher instanceof PushedFilePropertiesUpdaterImpl) {
          ((PushedFilePropertiesUpdaterImpl)myPusher).applyPushersToFile(fileOrDir, pusherExs, moduleValues);
        }

        UnindexedFileStatus status;
        long statusTime = System.nanoTime();
        try {
          status = ourTestMode == TestMode.PUSHING ? null : unindexedFileFinder.getFileStatus(fileOrDir);
        }
        finally {
          statusTime = System.nanoTime() - statusTime;
        }
        if (status != null) {
          if (status.getShouldIndex() && ourTestMode == null) {
            files.add(fileOrDir);
          }
          scanningStatistics.addStatus(fileOrDir, status, statusTime, project);
        }
        scanningStatistics.addScanningTime(System.nanoTime() - scanningStart);
        return true;
      };
      return () -> {
        subTaskIndicator.setText(provider.getRootsScanningProgressText());
        try {
          provider.iterateFiles(project, collectingIterator, thisProviderDeduplicateFilter);
        }
        finally {
          scanningStatistics.setNumberOfSkippedFiles(thisProviderDeduplicateFilter.getNumberOfSkippedFiles());
          synchronized (allTasksFinished) {
            if (!allTasksFinished.get()) {
              projectIndexingHistory.addScanningStatistics(scanningStatistics);
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
    return providerToFiles;
  }

  private void scheduleInitialVfsRefresh() {
    var projectId = myProject.getLocationHash();
    LOG.info(projectId + ": marking roots for initial VFS refresh");
    ProjectRootManagerEx.getInstanceEx(myProject).markRootsForRefresh();

    LOG.info(projectId + ": starting initial VFS refresh");
    var app = ApplicationManager.getApplication();
    var t = System.nanoTime();
    if (!app.isCommandLine() || CoreProgressManager.shouldKeepTasksAsynchronousInHeadlessMode()) {
      var sessionId = VirtualFileManager.getInstance().asyncRefresh(() -> timeInitialVfsRefresh(t));
      var connection = app.getMessageBus().connect();
      connection.subscribe(ProjectCloseListener.TOPIC, new ProjectCloseListener() {
        @Override
        public void projectClosed(@NotNull Project project) {
          if (project == myProject) {
            LOG.info(projectId + ": cancelling initial VFS refresh");
            RefreshQueue.getInstance().cancelSession(sessionId);
            connection.disconnect();
          }
        }
      });
    }
    else {
      ApplicationManager.getApplication().invokeAndWait(() -> {
        VirtualFileManager.getInstance().syncRefresh();
        timeInitialVfsRefresh(t);
      });
    }
  }

  private void timeInitialVfsRefresh(long t) {
    var duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t);
    LOG.info(myProject.getLocationHash() + ": initial VFS refresh finished " + duration + " ms");
    VfsUsageCollector.logInitialRefresh(myProject, duration);
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
    myIndex.loadIndexes();
    myIndex.filesUpdateStarted(myProject, isFullIndexUpdate());
    IndexDiagnosticDumper.getInstance().onIndexingStarted(projectIndexingHistory);
    Ref<StatusMark> markRef = new Ref<>();
    try {
      ((GistManagerImpl)GistManager.getInstance()).
        runWithMergingDependentCacheInvalidations(() -> scanAndUpdateUnindexedFiles(projectIndexingHistory, indicator, markRef));
    }
    catch (Throwable e) {
      projectIndexingHistory.setWasInterrupted(true);
      if (e instanceof ControlFlowException) {
        LOG.info("Cancelled indexing of " + myProject.getName());
      }
      throw e;
    }
    finally {
      myIndex.filesUpdateFinished(myProject);
      projectIndexingHistory.finishTotalUpdatingTime();
      if (DependenciesIndexedStatusService.shouldBeUsed() && IndexInfrastructure.hasIndices()) {
        DependenciesIndexedStatusService.getInstance(myProject)
          .indexingFinished(!projectIndexingHistory.getTimes().getWasInterrupted(), markRef.get());
      }
      IndexDiagnosticDumper.getInstance().onIndexingFinished(projectIndexingHistory);
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
}
