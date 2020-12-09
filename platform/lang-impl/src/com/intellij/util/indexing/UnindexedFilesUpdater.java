// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.impl.ProgressSuspender;
import com.intellij.openapi.project.*;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater;
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdaterImpl;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.RefreshQueue;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.containers.ConcurrentBitSet;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.contentQueue.IndexUpdateRunner;
import com.intellij.util.indexing.diagnostic.IndexDiagnosticDumper;
import com.intellij.util.indexing.diagnostic.IndexingJobStatistics;
import com.intellij.util.indexing.diagnostic.ProjectIndexingHistory;
import com.intellij.util.indexing.diagnostic.ScanningStatistics;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import com.intellij.util.indexing.roots.SdkIndexableFilesIterator;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.progress.ConcurrentTasksProgressManager;
import com.intellij.util.progress.SubTaskProgressIndicator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

public final class UnindexedFilesUpdater extends DumbModeTask {
  private static final Logger LOG = Logger.getInstance(UnindexedFilesUpdater.class);
  private static final int DEFAULT_MAX_INDEXER_THREADS = 4;

  public enum TestMode {
    PUSHING, PUSHING_AND_SCANNING
  }
  @VisibleForTesting
  public static volatile TestMode ourTestMode;

  public static final ExecutorService GLOBAL_INDEXING_EXECUTOR = AppExecutorUtil.createBoundedApplicationPoolExecutor(
    "Indexing", getMaxNumberOfIndexingThreads()
  );
  private static final @NotNull Key<Boolean> CONTENT_SCANNED = Key.create("CONTENT_SCANNED");
  private static final @NotNull Key<UnindexedFilesUpdater> RUNNING_TASK = Key.create("RUNNING_INDEX_UPDATER_TASK");
  private static final Object ourLastRunningTaskLock = new Object();

  private final FileBasedIndexImpl myIndex = (FileBasedIndexImpl)FileBasedIndex.getInstance();
  private final Project myProject;
  private final boolean myStartSuspended;
  private final boolean myRunExtensionsForFilesMarkedAsIndexed;
  private final PushedFilePropertiesUpdater myPusher;

  public UnindexedFilesUpdater(@NotNull Project project, boolean startSuspended, boolean runExtensionsForFilesMarkedAsIndexed) {
    super(project);
    myProject = project;
    myStartSuspended = startSuspended;
    myRunExtensionsForFilesMarkedAsIndexed = runExtensionsForFilesMarkedAsIndexed;
    myPusher = PushedFilePropertiesUpdater.getInstance(myProject);
    myProject.putUserData(CONTENT_SCANNED, null);

    synchronized (ourLastRunningTaskLock) {
      UnindexedFilesUpdater runningTask = myProject.getUserData(RUNNING_TASK);
      if (runningTask != null) {
        DumbService.getInstance(project).cancelTask(runningTask);
      }
      myProject.putUserData(RUNNING_TASK, this);
    }
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
    this(project, false, !isProjectContentFullyScanned(project));
  }

  private void updateUnindexedFiles(@NotNull ProjectIndexingHistory projectIndexingHistory, @NotNull ProgressIndicator indicator) {
    if (!IndexInfrastructure.hasIndices()) return;

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

    indicator.setIndeterminate(true);
    indicator.setText(IndexingBundle.message("progress.indexing.scanning"));

    boolean trackResponsiveness = !ApplicationManager.getApplication().isUnitTestMode();

    PerformanceWatcher.Snapshot snapshot = PerformanceWatcher.takeSnapshot();
    Instant pushPropertiesStart = Instant.now();
    try {
      myPusher.pushAllPropertiesNow();
    } finally {
      projectIndexingHistory.getTimes().setPushPropertiesDuration(Duration.between(pushPropertiesStart, Instant.now()));
    }

    if (trackResponsiveness) snapshot.logResponsivenessSinceCreation("Pushing properties");

    myIndex.clearIndicesIfNecessary();

    snapshot = PerformanceWatcher.takeSnapshot();

    Instant indexExtensionsStart = Instant.now();
    try {
      FileBasedIndexInfrastructureExtension.EP_NAME.extensions().forEach(ex -> ex.processIndexingProject(myProject, indicator));
    } finally {
      projectIndexingHistory.getTimes().setIndexExtensionsDuration(Duration.between(indexExtensionsStart, Instant.now()));
    }

    Instant scanFilesStart = Instant.now();
    List<IndexableFilesIterator> orderedProviders;
    Map<IndexableFilesIterator, List<VirtualFile>> providerToFiles;
    try {
      orderedProviders = getOrderedProviders();
      providerToFiles = collectIndexableFilesConcurrently(myProject, indicator, orderedProviders, projectIndexingHistory);
      myProject.putUserData(CONTENT_SCANNED, true);
    } finally {
      projectIndexingHistory.getTimes().setScanFilesDuration(Duration.between(scanFilesStart, Instant.now()));
    }

    if (trackResponsiveness) snapshot.logResponsivenessSinceCreation("Indexable file iteration");

    if (!ApplicationManager.getApplication().isUnitTestMode()) {
      // full VFS refresh makes sense only after it's loaded, i.e. after scanning files to index is finished
      scheduleInitialVfsRefresh();
    }

    int totalFiles = providerToFiles.values().stream().mapToInt(it -> it.size()).sum();
    if (trackResponsiveness) {
      LOG.info("Unindexed files update started: " + totalFiles + " files to index");
    }

    if (totalFiles == 0 || SystemProperties.getBooleanProperty("idea.indexes.pretendNoFiles", false)) {
      return;
    }

    snapshot = PerformanceWatcher.takeSnapshot();

    ProgressIndicator poweredIndicator = PoweredProgressIndicator.wrap(indicator, getPowerForSmoothProgressIndicator());
    poweredIndicator.setIndeterminate(false);
    poweredIndicator.setFraction(0);
    poweredIndicator.setText(IndexingBundle.message("progress.indexing.updating"));
    ConcurrentTasksProgressManager concurrentTasksProgressManager = new ConcurrentTasksProgressManager(poweredIndicator, totalFiles);

    int numberOfIndexingThreads = getNumberOfIndexingThreads();
    LOG.info("Using " + numberOfIndexingThreads + " indexing " + StringUtil.pluralize("thread", numberOfIndexingThreads) +
             ", " + getNumberOfScanningThreads() + " scanning " + StringUtil.pluralize("thread", numberOfIndexingThreads));
    IndexUpdateRunner indexUpdateRunner = new IndexUpdateRunner(myIndex, GLOBAL_INDEXING_EXECUTOR, numberOfIndexingThreads);

    Instant startIndexing = Instant.now();
    try {
      for (IndexableFilesIterator provider : orderedProviders) {
        List<VirtualFile> providerFiles = providerToFiles.get(provider);
        if (providerFiles == null || providerFiles.isEmpty()) {
          continue;
        }
        concurrentTasksProgressManager.setText(provider.getIndexingProgressText());
        SubTaskProgressIndicator subTaskIndicator = concurrentTasksProgressManager.createSubTaskIndicator(providerFiles.size());
        try {
          IndexingJobStatistics statistics;
          IndexUpdateRunner.IndexingInterruptedException exception = null;
          try {
            statistics = indexUpdateRunner.indexFiles(myProject, provider.getDebugName(), providerFiles, subTaskIndicator);
          }
          catch (IndexUpdateRunner.IndexingInterruptedException e) {
            exception = e;
            statistics = e.myStatistics;
          }

          try {
            projectIndexingHistory.addProviderStatistics(statistics);
          }
          catch (Exception e) {
            LOG.error("Failed to add indexing statistics for " + provider.getDebugName(), e);
          }

          if (exception != null) {
            ExceptionUtil.rethrow(exception.getCause());
          }
        }
        finally {
          subTaskIndicator.finished();
        }
      }
    } finally {
      projectIndexingHistory.getTimes().setIndexingDuration(Duration.between(startIndexing, Instant.now()));
    }

    if (trackResponsiveness) snapshot.logResponsivenessSinceCreation("Unindexed files update");
    myIndex.dumpIndexStatistics();
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

  static boolean isProjectContentFullyScanned(@NotNull Project project) {
    return Boolean.TRUE.equals(project.getUserData(CONTENT_SCANNED));
  }

  /**
   * Returns providers of files. Since LAB-22 (Smart Dumb Mode) is not implemented yet, the order of the providers is not strictly specified.
   * For shared indexes it is a good idea to index JDKs in the last turn (because they likely have shared index available)
   * so this method moves all SDK providers to the end.
   */
  @NotNull
  private List<IndexableFilesIterator> getOrderedProviders() {
    List<IndexableFilesIterator> originalOrderedProviders = myIndex.getOrderedIndexableFilesProviders(myProject);

    List<IndexableFilesIterator> orderedProviders = new ArrayList<>();
    originalOrderedProviders.stream()
      .filter(p -> !(p instanceof SdkIndexableFilesIterator))
      .collect(Collectors.toCollection(() -> orderedProviders));

    originalOrderedProviders.stream()
      .filter(p -> p instanceof SdkIndexableFilesIterator)
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
    UnindexedFilesFinder unindexedFileFilter = new UnindexedFilesFinder(project, myIndex, myRunExtensionsForFilesMarkedAsIndexed);
    Map<IndexableFilesIterator, List<VirtualFile>> providerToFiles = new IdentityHashMap<>();
    ConcurrentBitSet visitedFileSet = ConcurrentBitSet.create();

    indicator.setText(IndexingBundle.message("progress.indexing.scanning"));
    indicator.setIndeterminate(false);
    indicator.setFraction(0);

    ConcurrentTasksProgressManager concurrentTasksProgressManager = new ConcurrentTasksProgressManager(indicator, providers.size());

    List<Runnable> tasks = ContainerUtil.map(providers, provider -> {
      SubTaskProgressIndicator subTaskIndicator = concurrentTasksProgressManager.createSubTaskIndicator(1);
      List<VirtualFile> files = new ArrayList<>();
      ScanningStatistics scanningStatistics = new ScanningStatistics(provider.getDebugName());
      providerToFiles.put(provider, files);
      ContentIterator collectingIterator = fileOrDir -> {
        if (subTaskIndicator.isCanceled()) {
          return false;
        }
        UnindexedFileStatus status;
        long statusTime = System.nanoTime();
        try {
          status = ourTestMode == TestMode.PUSHING ? null : unindexedFileFilter.getFileStatus(fileOrDir);
        }
        finally {
          statusTime = System.nanoTime() - statusTime;
        }
        if (status != null) {
          if (status.getShouldIndex() && ourTestMode == null) {
            files.add(fileOrDir);
          }
          scanningStatistics.addStatus(status, statusTime);
        }
        return true;
      };
      return () -> {
        subTaskIndicator.setText(provider.getRootsScanningProgressText());
        try {
          provider.iterateFiles(project, collectingIterator, visitedFileSet);
        }
        finally {
          synchronized (projectIndexingHistory) {
            projectIndexingHistory.addScanningStatistics(scanningStatistics);
          }
          subTaskIndicator.finished();
        }
      };
    });
    PushedFilePropertiesUpdaterImpl.invokeConcurrentlyIfPossible(tasks);
    return providerToFiles;
  }

  private void scheduleInitialVfsRefresh() {
    ProjectRootManagerEx.getInstanceEx(myProject).markRootsForRefresh();

    Application app = ApplicationManager.getApplication();
    if (!app.isCommandLine()) {
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
    ProjectIndexingHistory projectIndexingHistory = new ProjectIndexingHistory(myProject);
    myIndex.filesUpdateStarted(myProject);
    try {
      updateUnindexedFiles(projectIndexingHistory, indicator);
    }
    catch (Throwable e) {
      projectIndexingHistory.getTimes().setWasInterrupted(true);
      if (e instanceof ControlFlowException) {
        LOG.info("Unindexed files update canceled");
      }
      throw e;
    }
    finally {
      myIndex.filesUpdateFinished(myProject);
      projectIndexingHistory.getTimes().setUpdatingEnd(ZonedDateTime.now(ZoneOffset.UTC));
      IndexDiagnosticDumper.INSTANCE.dumpProjectIndexingHistoryIfNecessary(projectIndexingHistory);
    }
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
