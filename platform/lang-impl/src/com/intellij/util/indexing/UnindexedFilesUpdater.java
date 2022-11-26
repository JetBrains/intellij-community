// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbServiceImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.SystemProperties;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.indexing.dependenciesCache.DependenciesIndexedStatusService;
import com.intellij.util.indexing.diagnostic.ScanningType;
import com.intellij.util.indexing.roots.IndexableFilesIterator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ExecutorService;

public class UnindexedFilesUpdater {
  // should be used only for test debugging purpose
  private static final Logger LOG = Logger.getInstance(UnindexedFilesUpdater.class);

  private static final boolean useConservativeThreadCountPolicy =
    SystemProperties.getBooleanProperty("idea.indexing.use.conservative.thread.count.policy", false);
  private static final int DEFAULT_MAX_INDEXER_THREADS = 4;
  // Allows to specify number of indexing threads. -1 means the default value (currently, 4).
  private static final int INDEXER_THREAD_COUNT = SystemProperties.getIntProperty("caches.indexerThreadsCount", -1);

  public static final ExecutorService GLOBAL_INDEXING_EXECUTOR = AppExecutorUtil.createBoundedApplicationPoolExecutor(
    "Indexing", getMaxNumberOfIndexingThreads()
  );
  private final Project myProject;
  private final boolean myStartSuspended;
  private final boolean myOnProjectOpen;
  private final String myIndexingReason;
  private final ScanningType myScanningType;
  private final DependenciesIndexedStatusService.StatusMark myMark;
  private final List<IndexableFilesIterator> myPredefinedIndexableFilesIterators;

  public UnindexedFilesUpdater(@NotNull Project project,
                               boolean startSuspended,
                               boolean onProjectOpen,
                               @Nullable List<IndexableFilesIterator> predefinedIndexableFilesIterators,
                               @Nullable DependenciesIndexedStatusService.StatusMark mark,
                               @Nullable @NonNls String indexingReason,
                               @NotNull ScanningType scanningType) {
    myProject = project;
    myStartSuspended = startSuspended;
    myOnProjectOpen = onProjectOpen;
    myIndexingReason = indexingReason;
    myScanningType = scanningType;
    myMark = mark;
    myPredefinedIndexableFilesIterators = predefinedIndexableFilesIterators;
    LOG.assertTrue(myPredefinedIndexableFilesIterators == null || !myPredefinedIndexableFilesIterators.isEmpty());
  }

  public UnindexedFilesUpdater(@NotNull Project project) {
    // If we haven't succeeded to fully scan the project content yet, then we must keep trying to run
    // file based index extensions for all project files until at least one of UnindexedFilesScanner-s finishes without cancellation.
    // This is important, for example, for shared indexes: all files must be associated with their locally available shared index chunks.
    this(project, false, false, null, null, null, ScanningType.FULL);
  }

  public UnindexedFilesUpdater(@NotNull Project project, @Nullable @NonNls String indexingReason) {
    this(project, false, false, null, null, indexingReason, ScanningType.FULL);
  }

  public UnindexedFilesUpdater(@NotNull Project project,
                               @Nullable List<IndexableFilesIterator> predefinedIndexableFilesIterators,
                               @Nullable DependenciesIndexedStatusService.StatusMark mark,
                               @Nullable @NonNls String indexingReason) {
    this(project, false, false, predefinedIndexableFilesIterators, mark, indexingReason,
         predefinedIndexableFilesIterators == null ? ScanningType.FULL : ScanningType.PARTIAL);
  }

  public void queue() {
    new UnindexedFilesScanner(myProject, myStartSuspended, myOnProjectOpen, myPredefinedIndexableFilesIterators, myMark, myIndexingReason,
                              myScanningType)
      .queue(myProject);
  }

  /**
   * Returns the best number of threads to be used for indexing at this moment.
   * It may change during execution of the IDE depending on other activities' load.
   */
  public static int getNumberOfIndexingThreads() {
    int threadCount = INDEXER_THREAD_COUNT;
    if (threadCount <= 0) {
      threadCount =
        Math.max(1, Math.min(useConservativeThreadCountPolicy
                             ? DEFAULT_MAX_INDEXER_THREADS : getMaxBackgroundThreadCount(), getMaxBackgroundThreadCount()));
    }
    return threadCount;
  }

  /**
   * Returns the maximum number of threads to be used for indexing during this execution of the IDE.
   */
  public static int getMaxNumberOfIndexingThreads() {
    // Change of the registry option requires IDE restart.
    int threadCount = INDEXER_THREAD_COUNT;
    return Math.max(1, threadCount <= 0 ? getMaxBackgroundThreadCount() : threadCount);
  }

  /**
   * Scanning activity can be scaled well across number of threads, so we're trying to use all available resources to do it faster.
   */
  public static int getNumberOfScanningThreads() {
    int scanningThreadCount = Registry.intValue("caches.scanningThreadsCount");
    if (scanningThreadCount > 0) return scanningThreadCount;
    int maxBackgroundThreadCount = getMaxBackgroundThreadCount();
    return Math.max(maxBackgroundThreadCount, getNumberOfIndexingThreads());
  }

  private static int getMaxBackgroundThreadCount() {
    int coresToLeaveForOtherActivity = DumbServiceImpl.ALWAYS_SMART
                                       ? getMaxNumberOfIndexingThreads() : ApplicationManager.getApplication().isCommandLine() ? 0 : 1;
    return Runtime.getRuntime().availableProcessors() - coresToLeaveForOtherActivity;
  }

  public static boolean isIndexUpdateInProgress(@NotNull Project project) {
    return UnindexedFilesScanner.isIndexUpdateInProgress(project);
  }
}
