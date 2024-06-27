// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.UnindexedFilesScannerExecutor;
import com.intellij.openapi.util.registry.Registry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import static com.intellij.util.SystemProperties.getBooleanProperty;
import static com.intellij.util.SystemProperties.getIntProperty;

public final class UnindexedFilesUpdater {
  private static final boolean useConservativeThreadCountPolicy = getBooleanProperty("idea.indexing.use.conservative.thread.count.policy", false);

  private static final int DEFAULT_MAX_INDEXER_THREADS = 4;
  /** Defines number of indexing threads. -1 means autoconfigured value (see getNumberOfIndexingThreads/getMaxNumberOfIndexingThreads for algo). */
  private static final int INDEXER_THREAD_COUNT = getIntProperty("caches.indexerThreadsCount", -1);
  /**
   * Count CPU# with or without hyper-threading:
   * if true:  assume # cores reported is # physical cores x2, so /2 to get physical cores count
   * If false (default): use # cores reported as-is, don't try to outsmart CPU developers
   */
  private static final boolean IS_HT_SMT_ENABLED = getBooleanProperty("intellij.system.ht.smt.enabled", false);

  private UnindexedFilesUpdater() {
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
                             ? DEFAULT_MAX_INDEXER_THREADS : getMaxNumberOfIndexingThreads(), getMaxNumberOfIndexingThreads()));
    }
    return threadCount;
  }

  /**
   * Returns the maximum number of threads to be used for indexing during this execution of the IDE.
   */
  public static int getMaxNumberOfIndexingThreads() {
    // Change of the registry option requires IDE restart.
    int threadCount = INDEXER_THREAD_COUNT;
    if (threadCount > 0) {
      return threadCount;
    }

    return Math.max(1, getAvailablePhysicalCoresNumber() - getCoresToLeaveForOtherActivitiesCount());
  }

  public static int getAvailablePhysicalCoresNumber() {
    var availableCores = Runtime.getRuntime().availableProcessors();
    return IS_HT_SMT_ENABLED ? availableCores / 2 : availableCores;
  }

  /**
   * Scanning activity can be scaled well across number of threads, so we're trying to use all available resources to do it faster.
   */
  public static @Range(from = 1, to = Integer.MAX_VALUE) int getNumberOfScanningThreads() {
    int scanningThreadCount = Registry.intValue("caches.scanningThreadsCount");
    if (scanningThreadCount > 0) return scanningThreadCount;
    int maxBackgroundThreadCount = getMaxBackgroundThreadCount();
    return Math.max(maxBackgroundThreadCount, getNumberOfIndexingThreads());
  }

  private static int getMaxBackgroundThreadCount() {
    // note that getMaxBackgroundThreadCount is used to calculate threads count is FilesScanExecutor, which is also used for "FindInFiles"
    return Runtime.getRuntime().availableProcessors() - getCoresToLeaveForOtherActivitiesCount();
  }

  private static int getCoresToLeaveForOtherActivitiesCount() {
    return ApplicationManager.getApplication().isCommandLine() ? 0 : 1;
  }

  public static boolean isScanningInProgress(@NotNull Project project) {
    UnindexedFilesScannerExecutor executor = UnindexedFilesScannerExecutor.getInstance(project);
    return executor.getHasQueuedTasks() || executor.isRunning().getValue();
  }
}
