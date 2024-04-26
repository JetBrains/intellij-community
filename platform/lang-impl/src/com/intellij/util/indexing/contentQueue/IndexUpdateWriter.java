// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.contentQueue;

import com.intellij.find.ngrams.TrigramIndex;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.psi.impl.cache.impl.id.IdIndex;
import com.intellij.psi.stubs.StubUpdatingIndex;
import com.intellij.util.SystemProperties;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import com.intellij.util.indexing.IndexId;
import com.intellij.util.indexing.UnindexedFilesUpdater;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

@ApiStatus.Internal
public final class IndexUpdateWriter {
  private static final Logger LOG = Logger.getInstance(IndexUpdateWriter.class);
  /**
   * When disabled, each indexing thread is equal and writing indexes by itself.
   * When enabled, indexing threads preparing updates and submitting writing to the dedicated threads: IdIndex, TrigramIndex, Stubs and rest.
   * By default, it is enabled for multiprocessor systems, where we can benefit from the parallel processing.
   */
  public static final boolean WRITE_INDEXES_ON_SEPARATE_THREAD =
    SystemProperties.getBooleanProperty("idea.write.indexes.on.separate.thread", UnindexedFilesUpdater.getMaxNumberOfIndexingThreads() > 5);

  /**
   * Base writers are for: IdIndex, Stubs and Trigrams
   */
  private static final int BASE_WRITERS_NUMBER = WRITE_INDEXES_ON_SEPARATE_THREAD ? 3 : 0;

  /**
   * Aux writers used to write other indexes in parallel. But each index is 100% written on the same thread.
   */
  private static final int AUX_WRITERS_NUMBER = WRITE_INDEXES_ON_SEPARATE_THREAD ? 1 : 0;

  /**
   * Max number of queued updates per indexing thread, after which one indexing thread is going to sleep, until queue is shrunk.
   */
  private static final int MAX_ALLOWED_WRITES_IN_QUEUE_PER_INDEXER = 100;

  /**
   * This is an experimental data from indexing IDEA project: median write time for a single index entry.
   */
  private static final long EXPECTED_SINGLE_WRITE_TIME_NS = 2_500;

  /**
   * Total number of index writing threads
   */
  public static final int TOTAL_WRITERS_NUMBER = BASE_WRITERS_NUMBER + AUX_WRITERS_NUMBER;

  /**
   * Time in milliseconds we are waiting writers to finish their job and shutdown.
   */
  private static final long WRITERS_SHUTDOWN_WAITING_TIME_MS = 10_000;

  /**
   * Number of asynchronous updates scheduled by {@link #scheduleIndexWriting(int, Runnable)}
   */
  private static final AtomicInteger INDEX_WRITES_QUEUED = new AtomicInteger();
  /**
   * Number of currently sleeping indexers, because of too large updates queue
   */
  private static final AtomicInteger SLEEPING_INDEXERS = new AtomicInteger();

  private static final List<ExecutorService> INDEX_WRITING_POOL;

  static {
    if (!WRITE_INDEXES_ON_SEPARATE_THREAD) {
      INDEX_WRITING_POOL = Collections.emptyList();
    }
    else {
      var pool = new ArrayList<ExecutorService>(TOTAL_WRITERS_NUMBER);
      pool.addAll(List.of(
        SequentialTaskExecutor.createSequentialApplicationPoolExecutor("IdIndex Writer"),
        SequentialTaskExecutor.createSequentialApplicationPoolExecutor("Stubs Writer"),
        SequentialTaskExecutor.createSequentialApplicationPoolExecutor("Trigram Writer")));

      for (int i = 0; i < AUX_WRITERS_NUMBER; i++) {
        pool.add(SequentialTaskExecutor.createSequentialApplicationPoolExecutor("Aux Index Writer #" + (i + 1)));
      }
      INDEX_WRITING_POOL = Collections.unmodifiableList(pool);
      LOG.assertTrue(INDEX_WRITING_POOL.size() == TOTAL_WRITERS_NUMBER);
    }
  }

  /**
   * Waiting till index writing threads finish their jobs.
   *
   * @see #WRITERS_SHUTDOWN_WAITING_TIME_MS
   */
  static void waitWritingThreadsToFinish() {
    if (INDEX_WRITING_POOL.isEmpty()) {
      return;
    }

    List<Future<?>> futures = new ArrayList<>(INDEX_WRITING_POOL.size());
    INDEX_WRITING_POOL.forEach(executor -> futures.add(executor.submit(EmptyRunnable.getInstance())));

    var startTime = System.currentTimeMillis();
    while (!futures.isEmpty()) {
      for (var iterator = futures.iterator(); iterator.hasNext(); ) {
        var future = iterator.next();
        if (future.isDone()) {
          iterator.remove();
        }
      }
      TimeoutUtil.sleep(10);
      if (System.currentTimeMillis() - startTime > WRITERS_SHUTDOWN_WAITING_TIME_MS) {
        var queueSize = INDEX_WRITES_QUEUED.get();
        var errorMessage = "Failed to shutdown index writers, queue size: " + queueSize + "; executors active: " + futures;
        if (queueSize == 0) {
          LOG.warn(errorMessage);
        }
        else {
          LOG.error(errorMessage);
        }
        return;
      }
    }
  }

  /**
   * @return executor index in {@link #INDEX_WRITING_POOL}.
   * Allow partitioning indexes writing to different threads to avoid concurrency.
   * We may add aux executors if necessary, system scheduler will handle the rest.
   */
  public static int getExecutorIndex(@NotNull IndexId<?, ?> indexId) {
    if (indexId == IdIndex.NAME) {
      return 0;
    }
    else if (indexId == StubUpdatingIndex.INDEX_ID) {
      return 1;
    }
    else if (indexId == TrigramIndex.INDEX_ID) {
      return 2;
    }
    return AUX_WRITERS_NUMBER == 1 ? BASE_WRITERS_NUMBER :
           BASE_WRITERS_NUMBER + Math.abs(indexId.getName().hashCode()) % AUX_WRITERS_NUMBER;
  }

  public static void scheduleIndexWriting(int executorIndex, @NotNull Runnable runnable) {
    INDEX_WRITES_QUEUED.incrementAndGet();
    INDEX_WRITING_POOL.get(executorIndex).execute(() -> {
      try {
        ProgressManager.getInstance().executeNonCancelableSection(runnable);
      }
      finally {
        INDEX_WRITES_QUEUED.decrementAndGet();
      }
    });
  }

  static void sleepIfWriterQueueLarge(int numberOfIndexingThreads) {
    var currentlySleeping = SLEEPING_INDEXERS.get();
    var couldBeSleeping = currentlySleeping + 1;
    int writesInQueueToSleep =
      MAX_ALLOWED_WRITES_IN_QUEUE_PER_INDEXER * numberOfIndexingThreads + couldBeSleeping * MAX_ALLOWED_WRITES_IN_QUEUE_PER_INDEXER;
    var writesInQueue = INDEX_WRITES_QUEUED.get();
    if (writesInQueue > writesInQueueToSleep && SLEEPING_INDEXERS.compareAndSet(currentlySleeping, couldBeSleeping)) {
      var writesToWakeUp = writesInQueueToSleep - MAX_ALLOWED_WRITES_IN_QUEUE_PER_INDEXER;
      LOG.debug("Sleeping indexer: ", couldBeSleeping, " of ", numberOfIndexingThreads, "; writes queued: ", writesInQueue,
                "; wake up when queue shrinks to ", writesToWakeUp);
      var napTimeNs = MAX_ALLOWED_WRITES_IN_QUEUE_PER_INDEXER * EXPECTED_SINGLE_WRITE_TIME_NS;
      try {
        long slept = sleepUntilUpdatesQueueIsShrunk(writesToWakeUp, napTimeNs);
        LOG.debug("Waking indexer ", SLEEPING_INDEXERS.get(), " of ", numberOfIndexingThreads, " by ", INDEX_WRITES_QUEUED.get(),
                  " updates in queue, should have wake up on ", writesToWakeUp,
                  "; slept for ", slept, " ms");
      }
      finally {
        SLEEPING_INDEXERS.decrementAndGet();
      }
    }
  }

  /**
   * Puts the indexing thread to the sleep until the queue of updates is shrunk enough to increase the number of indexing threads.
   * To balance load better, each next sleeping indexer checks for the queue more frequently.
   */
  private static long sleepUntilUpdatesQueueIsShrunk(int writesToWakeUp, long napTimeNs) {
    var sleepStart = System.nanoTime();
    int iterations = 1;
    while (writesToWakeUp < INDEX_WRITES_QUEUED.get()) {
      LockSupport.parkNanos(napTimeNs * iterations);
      iterations++;
    }
    return (System.nanoTime() - sleepStart) / 1_000_000;
  }
}
