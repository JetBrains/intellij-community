// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.progress.impl.ProgressSuspender;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.*;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.impl.FilePropertyPusher;
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater;
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdaterImpl;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.contentQueue.IndexUpdateRunner;
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper;
import com.intellij.util.indexing.diagnostic.ProjectIndexingHistory;
import com.intellij.util.indexing.diagnostic.ScanningStatistics;
import com.intellij.util.indexing.diagnostic.dto.JsonScanningStatistics;
import com.intellij.util.indexing.roots.IndexableFileScanner;
import com.intellij.util.indexing.roots.IndexableFilesDeduplicateFilter;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.util.indexing.roots.kind.IndexableSetOrigin;
import com.intellij.util.indexing.roots.kind.ModuleRootOrigin;
import com.intellij.util.indexing.roots.kind.SdkOrigin;
import com.intellij.util.indexing.snapshot.SnapshotInputMappingsStatistics;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.progress.ConcurrentTasksProgressManager;
import com.intellij.util.progress.SubTaskProgressIndicator;
import org.jetbrains.annotations.*;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.intellij.openapi.roots.impl.PushedFilePropertiesUpdaterImpl.getModuleImmediateValues;

@ApiStatus.Internal
public final class UnindexedFilesUpdater extends DumbModeTask {
  private static final Logger LOG = Logger.getInstance(UnindexedFilesUpdater.class);
  private static final int DEFAULT_MAX_INDEXER_THREADS = 4;

  public enum TestMode {
    PUSHING, PUSHING_AND_SCANNING
  }
  @SuppressWarnings("StaticNonFinalField") @VisibleForTesting
  public static volatile TestMode ourTestMode;

  public static final ExecutorService GLOBAL_INDEXING_EXECUTOR = AppExecutorUtil.createBoundedApplicationPoolExecutor(
    "Indexing", getMaxNumberOfIndexingThreads()
  );
  private static final int MINIMUM_NUMBER_OF_FILES_TO_RUN_CONCURRENT_INDEXING =
    SystemProperties.getIntProperty("intellij.indexing.minimum.number.of.files.to.run.concurrent.indexing", 100);
  private static final @NotNull Key<Boolean> CONTENT_SCANNED = Key.create("CONTENT_SCANNED");
  private static final @NotNull Key<Boolean> INDEX_UPDATE_IN_PROGRESS = Key.create("INDEX_UPDATE_IN_PROGRESS");
  private static final @NotNull Key<UnindexedFilesUpdater> RUNNING_TASK = Key.create("RUNNING_INDEX_UPDATER_TASK");
  private static final Object ourLastRunningTaskLock = new Object();

  private final FileBasedIndexImpl myIndex = (FileBasedIndexImpl)FileBasedIndex.getInstance();
  private final Project myProject;
  private final boolean myStartSuspended;
  private final @NonNls String myIndexingReason;
  private final PushedFilePropertiesUpdater myPusher;
  private final @Nullable List<IndexableFilesIterator> myPredefinedIndexableFilesIterators;

  public UnindexedFilesUpdater(@NotNull Project project,
                               boolean startSuspended,
                               @Nullable List<IndexableFilesIterator> predefinedIndexableFilesIterators,
                               @Nullable @NonNls String indexingReason) {
    super(project);
    myProject = project;
    myStartSuspended = startSuspended;
    myIndexingReason = indexingReason;
    myPusher = PushedFilePropertiesUpdater.getInstance(myProject);
    myProject.putUserData(CONTENT_SCANNED, null);

    synchronized (ourLastRunningTaskLock) {
      UnindexedFilesUpdater runningTask = myProject.getUserData(RUNNING_TASK);
      //two tasks with non-null myPredefinedIndexableFilesIterators should be just run one after other
      if (runningTask == null || runningTask.myPredefinedIndexableFilesIterators == null || predefinedIndexableFilesIterators == null) {
        myProject.putUserData(RUNNING_TASK, this);

        if (runningTask != null) {
          if (runningTask.myPredefinedIndexableFilesIterators == null && predefinedIndexableFilesIterators != null) {
            //new task should be run for all changes to handle what wasn't handled by the previous one
            predefinedIndexableFilesIterators = null;
          }
          DumbService.getInstance(project).cancelTask(runningTask);
        }
      }
    }
    myPredefinedIndexableFilesIterators = predefinedIndexableFilesIterators;
  }

  @Override
  public void dispose() {
    synchronized (ourLastRunningTaskLock) {
      UnindexedFilesUpdater lastRunningTask = myProject.getUserData(RUNNING_TASK);
      if (lastRunningTask == this) {
        myProject.putUserData(RUNNING_TASK, null);
      }
    }
  }

  public UnindexedFilesUpdater(@NotNull Project project) {
    // If we haven't succeeded to fully scan the project content yet, then we must keep trying to run
    // file based index extensions for all project files until at least one of UnindexedFilesUpdater-s finishes without cancellation.
    // This is important, for example, for shared indexes: all files must be associated with their locally available shared index chunks.
    this(project, false, null, null);
  }

  public UnindexedFilesUpdater(@NotNull Project project, @Nullable @NonNls String indexingReason) {
    this(project, false, null, indexingReason);
  }

  public UnindexedFilesUpdater(@NotNull Project project, @Nullable List<IndexableFilesIterator> predefinedIndexableFilesIterators, @Nullable @NonNls String indexingReason) {
    this(project, false, predefinedIndexableFilesIterators, indexingReason);
  }

  private void updateUnindexedFiles(@NotNull ProjectIndexingHistory projectIndexingHistory, @NotNull ProgressIndicator indicator) {
    if (!IndexInfrastructure.hasIndices()) return;
    LOG.info("Started indexing of " + myProject.getName() + (myIndexingReason == null ? "" : ". Reason: " + myIndexingReason));

    ProgressSuspender suspender = ProgressSuspender.getSuspender(indicator);
    if (suspender != null) {
      listenToProgressSuspenderForSuspendedTimeDiagnostic(suspender, projectIndexingHistory);
    }

    if (myStartSuspended) {
      if (suspender == null) {
        throw new IllegalStateException("Indexing progress indicator must be suspendable!");
      }
      if (!suspender.isSuspended()) {
        suspender.suspendProcess(IndexingBundle.message("progress.indexing.started.as.suspended"));
      }
    }

    PerformanceWatcher.Snapshot snapshot = PerformanceWatcher.takeSnapshot();
    Instant pushPropertiesStart = Instant.now();
    try {
      if (myPusher instanceof PushedFilePropertiesUpdaterImpl) {
        ((PushedFilePropertiesUpdaterImpl)myPusher).performDelayedPushTasks();
      }
    } finally {
      projectIndexingHistory.getTimes().setPushPropertiesDuration(Duration.between(pushPropertiesStart, Instant.now()));
    }
    LOG.info(snapshot.getLogResponsivenessSinceCreationMessage("Performing delayed pushing properties tasks for " + myProject.getName()));


    indicator.setIndeterminate(true);
    indicator.setText(IndexingBundle.message("progress.indexing.scanning"));

    snapshot = PerformanceWatcher.takeSnapshot();
    Instant scanFilesStart = Instant.now();
    List<IndexableFilesIterator> orderedProviders;
    Map<IndexableFilesIterator, List<VirtualFile>> providerToFiles;
    try {
      orderedProviders = getOrderedProviders();
      providerToFiles = collectIndexableFilesConcurrently(myProject, indicator, orderedProviders, projectIndexingHistory);
      if (myPredefinedIndexableFilesIterators == null) {
        myProject.putUserData(CONTENT_SCANNED, true);
      }
    } finally {
      projectIndexingHistory.getTimes().setScanFilesDuration(Duration.between(scanFilesStart, Instant.now()));
    }
    String scanningCompletedMessage = getLogScanningCompletedStageMessage(projectIndexingHistory);
    LOG.info(snapshot.getLogResponsivenessSinceCreationMessage(scanningCompletedMessage));

    myIndex.clearIndicesIfNecessary();

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      // full VFS refresh makes sense only after it's loaded, i.e. after scanning files to index is finished
      scheduleInitialVfsRefresh();
    }

    int totalFiles = providerToFiles.values().stream().mapToInt(it -> it.size()).sum();
    if (totalFiles == 0) {
      LOG.info("Finished for " + myProject.getName() + ". No files to index with loading content.");
      return;
    }
    if (SystemProperties.getBooleanProperty("idea.indexes.pretendNoFiles", false)) {
      LOG.info("Finished for " + myProject.getName() + ". System property 'idea.indexes.pretendNoFiles' is enabled.");
      return;
    }

    snapshot = PerformanceWatcher.takeSnapshot();

    ProgressIndicator poweredIndicator = PoweredProgressIndicator.wrap(indicator, getPowerForSmoothProgressIndicator());
    poweredIndicator.setIndeterminate(false);
    poweredIndicator.setFraction(0);
    poweredIndicator.setText(IndexingBundle.message("progress.indexing.updating"));

    myIndex.resetSnapshotInputMappingStatistics();

    Instant startIndexing = Instant.now();
    try {
      indexFiles(orderedProviders, providerToFiles, projectIndexingHistory, poweredIndicator);
    } finally {
      projectIndexingHistory.getTimes().setIndexingDuration(Duration.between(startIndexing, Instant.now()));
    }

    LOG.info(snapshot.getLogResponsivenessSinceCreationMessage("Finished for " + myProject.getName() + ". Unindexed files update"));
    List<SnapshotInputMappingsStatistics> snapshotInputMappingsStatistics = myIndex.dumpSnapshotInputMappingStatistics();
    projectIndexingHistory.addSnapshotInputMappingStatistics(snapshotInputMappingsStatistics);
  }

  private void indexFiles(@NotNull List<IndexableFilesIterator> orderedProviders,
                          @NotNull Map<IndexableFilesIterator, List<VirtualFile>> providerToFiles,
                          @NotNull ProjectIndexingHistory projectIndexingHistory,
                          @NotNull ProgressIndicator progressIndicator) {
    int totalFiles = providerToFiles.values().stream().mapToInt(it -> it.size()).sum();
    ConcurrentTasksProgressManager concurrentTasksProgressManager = new ConcurrentTasksProgressManager(progressIndicator, totalFiles);

    int numberOfIndexingThreads = getNumberOfIndexingThreads();
    LOG.info("Use " + numberOfIndexingThreads + " indexing " + StringUtil.pluralize("thread", numberOfIndexingThreads) +
             " for indexing of " + myProject.getName());
    IndexUpdateRunner indexUpdateRunner = new IndexUpdateRunner(myIndex, GLOBAL_INDEXING_EXECUTOR, numberOfIndexingThreads);

    for (int index = 0; index < orderedProviders.size(); ) {
      List<IndexUpdateRunner.FileSet> fileSets = new ArrayList<>();
      int takenFiles = 0;

      int biggestProviderFiles = 0;
      IndexableFilesIterator biggestProvider = null;

      while (takenFiles < MINIMUM_NUMBER_OF_FILES_TO_RUN_CONCURRENT_INDEXING && index < orderedProviders.size()) {
        IndexableFilesIterator provider = orderedProviders.get(index++);
        List<VirtualFile> providerFiles = providerToFiles.getOrDefault(provider, Collections.emptyList());
        if (!providerFiles.isEmpty()) {
          fileSets.add(new IndexUpdateRunner.FileSet(myProject, provider.getDebugName(), providerFiles));
        }
        if (biggestProviderFiles < providerFiles.size()) {
          biggestProviderFiles = providerFiles.size();
          biggestProvider = provider;
        }
        takenFiles += providerFiles.size();
      }
      if (fileSets.isEmpty() || biggestProvider == null) {
        break;
      }

      var indexingProgressText = biggestProvider.getIndexingProgressText();

      concurrentTasksProgressManager.setText(indexingProgressText);
      int setFilesNumber = fileSets.stream().mapToInt(b -> b.files.size()).sum();
      SubTaskProgressIndicator subTaskIndicator = concurrentTasksProgressManager.createSubTaskIndicator(setFilesNumber);
      try {
        IndexUpdateRunner.IndexingInterruptedException exception = null;
        try {
          indexUpdateRunner.indexFiles(myProject, fileSets, subTaskIndicator);
        }
        catch (IndexUpdateRunner.IndexingInterruptedException e) {
          exception = e;
        }

        try {
          fileSets.forEach(b -> projectIndexingHistory.addProviderStatistics(b.statistics));
        }
        catch (Exception e) {
          LOG.error("Failed to add indexing statistics", e);
        }

        if (exception != null) {
          ExceptionUtil.rethrow(exception.getCause());
        }
      }
      finally {
        subTaskIndicator.finished();
      }
    }
  }

  private static @NotNull String getLogScanningCompletedStageMessage(@NotNull ProjectIndexingHistory projectIndexingHistory) {
    List<JsonScanningStatistics> statistics = projectIndexingHistory.getScanningStatistics();
    int numberOfScannedFiles = statistics.stream().mapToInt(s -> s.getNumberOfScannedFiles()).sum();
    int numberOfFilesForIndexing = statistics.stream().mapToInt(s -> s.getNumberOfFilesForIndexing()).sum();
    return "Scanning completed for " + projectIndexingHistory.getProject().getName() + ". Number of scanned files: " + numberOfScannedFiles + "; " +
             "Number of files for indexing: " + numberOfFilesForIndexing;
  }

  private void listenToProgressSuspenderForSuspendedTimeDiagnostic(@NotNull ProgressSuspender suspender,
                                                                   @NotNull ProjectIndexingHistory projectIndexingHistory) {
    MessageBusConnection connection = ApplicationManager.getApplication().getMessageBus().connect(this);
    connection.subscribe(ProgressSuspender.TOPIC, new ProgressSuspender.SuspenderListener() {

      private volatile Instant suspensionStart = null;

      @Override
      public void suspendedStatusChanged(@NotNull ProgressSuspender changedSuspender) {
        if (suspender == changedSuspender) {
          if (suspender.isSuspended()) {
            suspensionStart = Instant.now();
          } else {
            Instant now = Instant.now();
            Instant start = suspensionStart;
            suspensionStart = null;
            if (start != null && start.compareTo(now) < 0) {
              Duration thisDuration = Duration.between(start, now);
              Duration currentTotalDuration = projectIndexingHistory.getTimes().getSuspendedDuration();
              Duration newTotalSuspendedDuration = currentTotalDuration.plus(thisDuration);
              projectIndexingHistory.getTimes().setSuspendedDuration(newTotalSuspendedDuration);
            }
          }
        }
      }
    });
  }

  public static boolean isIndexUpdateInProgress(@NotNull Project project) {
    return project.getUserData(INDEX_UPDATE_IN_PROGRESS) == Boolean.TRUE;
  }

  public static boolean isProjectContentFullyScanned(@NotNull Project project) {
    return Boolean.TRUE.equals(project.getUserData(CONTENT_SCANNED));
  }

  /**
   * Returns providers of files. Since LAB-22 (Smart Dumb Mode) is not implemented yet, the order of the providers is not strictly specified.
   * For shared indexes it is a good idea to index JDKs in the last turn (because they likely have shared index available)
   * so this method moves all SDK providers to the end.
   */
  @NotNull
  private List<IndexableFilesIterator> getOrderedProviders() {
    if (myPredefinedIndexableFilesIterators != null) return myPredefinedIndexableFilesIterators;

    List<IndexableFilesIterator> originalOrderedProviders = myIndex.getOrderedIndexableFilesProviders(myProject);

    List<IndexableFilesIterator> orderedProviders = new ArrayList<>();
    originalOrderedProviders.stream()
      .filter(p -> !(p.getOrigin() instanceof SdkOrigin))
      .collect(Collectors.toCollection(() -> orderedProviders));

    originalOrderedProviders.stream()
      .filter(p -> p.getOrigin() instanceof SdkOrigin)
      .collect(Collectors.toCollection(() -> orderedProviders));

    return orderedProviders;
  }

  @NotNull
  private Map<IndexableFilesIterator, List<VirtualFile>> collectIndexableFilesConcurrently(
    @NotNull Project project,
    @NotNull ProgressIndicator indicator,
    @NotNull List<IndexableFilesIterator> providers,
    @NotNull ProjectIndexingHistory projectIndexingHistory
  ) {
    if (providers.isEmpty()) {
      return Collections.emptyMap();
    }
    List<IndexableFileScanner.ScanSession> sessions =
      ContainerUtil.map(IndexableFileScanner.EP_NAME.getExtensionList(), scanner -> scanner.startSession(project));
    UnindexedFilesFinder unindexedFileFinder = new UnindexedFilesFinder(project, myIndex);

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
      providerToFiles.put(provider, files);
      IndexableSetOrigin origin = provider.getOrigin();
      List<IndexableFileScanner.@NotNull IndexableFileVisitor> fileScannerVisitors =
        ContainerUtil.mapNotNull(sessions, s -> s.createVisitor(origin));

      IndexableFilesDeduplicateFilter thisProviderDeduplicateFilter =
        IndexableFilesDeduplicateFilter.createDelegatingTo(indexableFilesDeduplicateFilter);

      List<FilePropertyPusher<?>> pushers;
      Object[] moduleValues;
      if (origin instanceof ModuleRootOrigin) {
        pushers = FilePropertyPusher.EP_NAME.getExtensionList();
        moduleValues = ReadAction.compute(() -> getModuleImmediateValues(pushers, (ModuleRootOrigin)origin));
      }
      else {
        pushers = null;
        moduleValues = null;
      }

      ProgressManager.checkCanceled(); // give a chance to suspend indexing
      ContentIterator collectingIterator = fileOrDir -> {

        ProgressManager.checkCanceled(); // give a chance to suspend indexing
        if (subTaskIndicator.isCanceled()) {
          return false;
        }

        PushedFilePropertiesUpdaterImpl.applyScannersToFile(fileOrDir, fileScannerVisitors);
        if (pushers != null && myPusher instanceof PushedFilePropertiesUpdaterImpl) {
          ((PushedFilePropertiesUpdaterImpl) myPusher).applyPushersToFile(fileOrDir, pushers, moduleValues);
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
    LOG.info("Scanning of " + myProject.getName() + " uses " + getNumberOfScanningThreads() + " scanning threads");
    try {
      PushedFilePropertiesUpdaterImpl.invokeConcurrentlyIfPossible(tasks);
    } finally {
      synchronized (allTasksFinished) {
        allTasksFinished.set(true);
      }
    }
    return providerToFiles;
  }

  private void scheduleInitialVfsRefresh() {
    ProjectRootManagerEx.getInstanceEx(myProject).markRootsForRefresh();

    Application app = ApplicationManager.getApplication();
    if (!app.isCommandLine() || CoreProgressManager.shouldKeepTasksAsynchronousInHeadlessMode()) {
      long sessionId = VirtualFileManager.getInstance().asyncRefresh(null);
      MessageBusConnection connection = app.getMessageBus().connect();
      connection.subscribe(ProjectManager.TOPIC, new ProjectManagerListener() {
        @Override
        public void projectClosed(@NotNull Project project) {
          if (project == myProject) {
            RefreshQueue.getInstance().cancelSession(sessionId);
            connection.disconnect();
          }
        }
      });
    }
    else {
      ApplicationManager.getApplication().invokeAndWait(() -> VirtualFileManager.getInstance().syncRefresh());
    }
  }

  private static double getPowerForSmoothProgressIndicator() {
    String rawValue = Registry.stringValue("indexing.progress.indicator.power");
    if ("-".equals(rawValue)) {
      return 1.0;
    }
    try {
      return Double.parseDouble(rawValue);
    }
    catch (NumberFormatException e) {
      return 1.0;
    }
  }

  @Override
  public void performInDumbMode(@NotNull ProgressIndicator indicator) {
    delayIndexingInTestsIfNecessary();
    myProject.putUserData(INDEX_UPDATE_IN_PROGRESS, true);
    ProjectIndexingHistory projectIndexingHistory = new ProjectIndexingHistory(myProject, myIndexingReason);
    myIndex.loadIndexes();
    myIndex.filesUpdateStarted(myProject);
    IndexDiagnosticDumper.getInstance().onIndexingStarted(projectIndexingHistory);
    try {
      updateUnindexedFiles(projectIndexingHistory, indicator);
    }
    catch (Throwable e) {
      projectIndexingHistory.getTimes().setWasInterrupted(true);
      if (e instanceof ControlFlowException) {
        LOG.info("Cancelled indexing of " + myProject.getName());
      }
      throw e;
    }
    finally {
      myIndex.filesUpdateFinished(myProject);
      myProject.putUserData(INDEX_UPDATE_IN_PROGRESS, false);
      projectIndexingHistory.getTimes().setUpdatingEnd(ZonedDateTime.now(ZoneOffset.UTC));
      projectIndexingHistory.getTimes().setTotalUpdatingTime(System.nanoTime() - projectIndexingHistory.getTimes().getTotalUpdatingTime());
      IndexDiagnosticDumper.getInstance().onIndexingFinished(projectIndexingHistory);
    }
  }

  private static void delayIndexingInTestsIfNecessary() {
    int delay = SystemProperties.getIntProperty("intellij.indexing.tests.indexing.delay.seconds", -1);
    long start = System.nanoTime();
    if (delay != -1) {
      ProgressIndicatorUtils.awaitWithCheckCanceled(() -> System.nanoTime() - start > TimeUnit.SECONDS.toNanos(delay));
    }
  }

  @Override
  public String toString() {
    return "UnindexedFilesUpdater[" + myProject.getName() + "]";
  }

  /**
   * Returns the best number of threads to be used for indexing at this moment.
   * It may change during execution of the IDE depending on other activities' load.
   */
  public static int getNumberOfIndexingThreads() {
    int threadCount = Registry.intValue("caches.indexerThreadsCount");
    if (threadCount <= 0) {
      int coresToLeaveForOtherActivity = ApplicationManager.getApplication().isCommandLine() ? 0 : 1;
      threadCount =
        Math.max(1, Math.min(Runtime.getRuntime().availableProcessors() - coresToLeaveForOtherActivity, DEFAULT_MAX_INDEXER_THREADS));
    }
    return threadCount;
  }

  /**
   * Returns the maximum number of threads to be used for indexing during this execution of the IDE.
   */
  public static int getMaxNumberOfIndexingThreads() {
    // Change of the registry option requires IDE restart.
    int threadCount = Registry.intValue("caches.indexerThreadsCount");
    return threadCount <= 0 ? DEFAULT_MAX_INDEXER_THREADS : threadCount;
  }

  /**
   * Scanning activity can be scaled well across number of threads, so we're trying to use all available resources to do it faster.
   */
  public static int getNumberOfScanningThreads() {
    int scanningThreadCount = Registry.intValue("caches.scanningThreadsCount");
    if (scanningThreadCount > 0) return scanningThreadCount;
    int coresToLeaveForOtherActivity = ApplicationManager.getApplication().isCommandLine() ? 0 : 1;
    return Math.max(Runtime.getRuntime().availableProcessors() - coresToLeaveForOtherActivity, getNumberOfIndexingThreads());
  }
}
