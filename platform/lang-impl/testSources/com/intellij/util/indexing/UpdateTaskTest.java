// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.junit5.TestApplication;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that when the same {@link UpdateTask} instance processes the same collection from different
 * threads in parallel, at least one invocation of {@link UpdateTask#processAll(Collection, Project)}
 * reports that some items were skipped (returns {@code false}).
 */
@TestApplication
public class UpdateTaskTest {
  /** Verifies deterministically that contention is reported even though the skipped item is eventually retried. */
  @Test
  public void processAllReturnsFalseAfterSkippedItemIsRetried() throws Exception {
    BlockingProcessingTask<Integer> task = new BlockingProcessingTask<>();
    CountDownLatch competingAttemptFinished = new CountDownLatch(1);

    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      Future<Boolean> firstResult = pool.submit(() -> task.processAll(List.of(1), null));
      assertTrue(task.firstProcessingStarted.await(10, TimeUnit.SECONDS), "The first processing attempt did not start");

      Future<Boolean> competingResult = pool.submit(
        () -> task.processAll(new AttemptSignalingCollection<>(competingAttemptFinished, 1), null)
      );
      try {
        assertTrue(competingAttemptFinished.await(10, TimeUnit.SECONDS), "The competing attempt did not reach the busy item");
      }
      finally {
        task.releaseFirstProcessing.countDown();
      }

      assertTrue(firstResult.get());
      assertFalse(competingResult.get(), "A call that skipped an item must report contention after retrying it");
      assertEquals(2, task.processingCount.get(), "Each invocation must eventually process the item once");
    }
  }

  /** Verifies that a retry pass contains only items skipped because of concurrent processing. */
  @Test
  public void onlyItemsSkippedDueToContentionAreRetried() throws Exception {
    SelectivelyBlockingProcessingTask task = new SelectivelyBlockingProcessingTask();

    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      Future<Boolean> blockingResult = pool.submit(() -> task.processAll(List.of(1), null));
      assertTrue(task.blockingItemProcessingStarted.await(10, TimeUnit.SECONDS), "The blocking item was not processed");

      Future<Boolean> competingResult = pool.submit(() -> task.processAll(List.of(1, 2), null));
      try {
        assertTrue(task.unrelatedItemProcessed.await(10, TimeUnit.SECONDS), "The unrelated item was not processed on the first pass");
      }
      finally {
        task.releaseBlockingItem.countDown();
      }

      assertTrue(blockingResult.get());
      assertFalse(competingResult.get());
      assertEquals(2, task.processingCount(1), "The contended item must be retried");
      assertEquals(1, task.processingCount(2), "An item processed successfully on the first pass must not be retried");
    }
  }

  /** Verifies the basic successful contract without concurrent invocations. */
  @Test
  public void processAllReturnsTrueWithoutContention() {
    RecordingTask task = new RecordingTask();

    assertTrue(task.processAll(List.of(1, 2), null));
    assertEquals(List.of(1, 2), task.processedItems, "Every item must be processed exactly once without contention");
  }

  /** Verifies that the task coordinates equal items without serializing unrelated ones. */
  @Test
  public void differentItemsAreProcessedConcurrently() throws Exception {
    ConcurrentEntryTracker tracker = new ConcurrentEntryTracker(2);
    ConcurrentProcessingTask<Integer> task = new ConcurrentProcessingTask<>(tracker);

    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      Future<Boolean> firstResult = pool.submit(() -> task.processAll(List.of(1), null));
      Future<Boolean> secondResult = pool.submit(() -> task.processAll(List.of(2), null));

      assertTrue(firstResult.get());
      assertTrue(secondResult.get());
      assertEquals(2, tracker.maxActiveEntries.get(), "Different items must not be serialized by the task semaphore");
    }
  }

  /** Verifies that exceptional processing does not leave the item occupied or the semaphore unbalanced. */
  @Test
  public void exceptionDoesNotLeakItemOrSemaphorePermit() {
    FailingOnceTask task = new FailingOnceTask();

    assertThrows(TestProcessingException.class, () -> task.processAll(List.of(1), null));
    assertTrue(task.processAll(List.of(1), null));
    assertEquals(2, task.processingCount.get(), "The item must remain processable after an exception");
  }

  /** Verifies that reentrancy fails fast and releases all state held by the outer invocation. */
  @Test
  public void reentrantProcessAllFailsFastAndDoesNotCorruptTask() {
    ReentrantOnceTask task = new ReentrantOnceTask();

    assertThrows(IllegalStateException.class, () -> task.processAll(List.of(1), null));
    assertTrue(task.processAll(List.of(1), null), "The task must remain usable after a rejected reentrant call");
    assertEquals(2, task.processingCount.get(), "The outer failed attempt must release item ownership");
  }

  /** Verifies directly that the same item is never passed to two doProcess calls concurrently. */
  @Test
  public void sameItemIsNeverProcessedConcurrently() throws Exception {
    BlockingProcessingTask<Integer> task = new BlockingProcessingTask<>();
    CountDownLatch competingAttemptFinished = new CountDownLatch(1);

    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      Future<Boolean> firstResult = pool.submit(() -> task.processAll(List.of(1), null));
      assertTrue(task.firstProcessingStarted.await(10, TimeUnit.SECONDS), "The first processing attempt did not start");

      Future<Boolean> competingResult = pool.submit(
        () -> task.processAll(new AttemptSignalingCollection<>(competingAttemptFinished, 1), null)
      );
      try {
        assertTrue(competingAttemptFinished.await(10, TimeUnit.SECONDS), "The competing attempt did not reach the busy item");
        assertEquals(1, task.maxActiveProcessingCount.get(), "Equal items must not be processed concurrently");
      }
      finally {
        task.releaseFirstProcessing.countDown();
      }

      assertTrue(firstResult.get());
      assertFalse(competingResult.get());
      assertEquals(1, task.maxActiveProcessingCount.get(), "Equal items must remain serialized during retry");
    }
  }

  /** Verifies that item exclusion is local to an UpdateTask instance rather than global. */
  @Test
  public void differentTaskInstancesDoNotCoordinate() throws Exception {
    ConcurrentEntryTracker tracker = new ConcurrentEntryTracker(2);
    ConcurrentProcessingTask<Integer> firstTask = new ConcurrentProcessingTask<>(tracker);
    ConcurrentProcessingTask<Integer> secondTask = new ConcurrentProcessingTask<>(tracker);

    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      Future<Boolean> firstResult = pool.submit(() -> firstTask.processAll(List.of(1), null));
      Future<Boolean> secondResult = pool.submit(() -> secondTask.processAll(List.of(1), null));

      assertTrue(firstResult.get());
      assertTrue(secondResult.get());
      assertEquals(2, tracker.maxActiveEntries.get(), "Different task instances must not share item ownership");
    }
  }

  /** Verifies that item exclusion follows equals/hashCode rather than object identity. */
  @Test
  public void equalItemsAreConsideredTheSame() throws Exception {
    BlockingProcessingTask<EqualItem> task = new BlockingProcessingTask<>();
    CountDownLatch competingAttemptFinished = new CountDownLatch(1);
    EqualItem firstItem = new EqualItem(1);
    EqualItem equalItem = new EqualItem(1);

    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      Future<Boolean> firstResult = pool.submit(() -> task.processAll(List.of(firstItem), null));
      assertTrue(task.firstProcessingStarted.await(10, TimeUnit.SECONDS), "The first processing attempt did not start");

      Future<Boolean> competingResult = pool.submit(
        () -> task.processAll(new AttemptSignalingCollection<>(competingAttemptFinished, equalItem), null)
      );
      try {
        assertTrue(competingAttemptFinished.await(10, TimeUnit.SECONDS), "The equal item did not contend with the occupied item");
      }
      finally {
        task.releaseFirstProcessing.countDown();
      }

      assertTrue(firstResult.get());
      assertFalse(competingResult.get(), "A distinct but equal item must be reported as skipped before retry");
      assertEquals(1, task.maxActiveProcessingCount.get(), "Distinct equal items must not be processed concurrently");
    }
  }

  /** Verifies that cancellation during semaphore waiting leaves the task reusable. */
  @Test
  public void cancellationWhileWaitingDoesNotCorruptTask() throws Exception {
    BlockingProcessingTask<Integer> task = new BlockingProcessingTask<>();
    CountDownLatch competingAttemptFinished = new CountDownLatch(1);
    EmptyProgressIndicator indicator = new EmptyProgressIndicator(ModalityState.nonModal());

    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      Future<Boolean> firstResult = pool.submit(() -> task.processAll(List.of(1), null));
      assertTrue(task.firstProcessingStarted.await(10, TimeUnit.SECONDS), "The first processing attempt did not start");

      Future<Boolean> canceledResult = pool.submit(() -> ProgressManager.getInstance().runProcess(
        () -> task.processAll(new AttemptSignalingCollection<>(competingAttemptFinished, 1), null),
        indicator
      ));
      try {
        assertTrue(competingAttemptFinished.await(10, TimeUnit.SECONDS), "The cancellable attempt did not reach the busy item");
        indicator.cancel();
        ExecutionException exception = assertThrows(ExecutionException.class, () -> canceledResult.get(10, TimeUnit.SECONDS));
        assertInstanceOf(ProcessCanceledException.class, exception.getCause());
      }
      finally {
        task.releaseFirstProcessing.countDown();
      }

      assertTrue(firstResult.get());
      assertTrue(task.processAll(List.of(1), null), "The task must remain usable after a waiting invocation is canceled");
    }
  }

  @Test
  public void processAllReturnsFalseSometimesWhenCalledInParallelOnSameCollection() throws Exception {
    SlowProcessingTask task = new SlowProcessingTask();

    // Use a small collection to maximize contention on the very first items
    Collection<Integer> items = Arrays.asList(1, 2, 3, 4, 5);

    // Start both workers at (almost) the same time to ensure they race on the first item
    int tasksCount = 2;
    CountDownLatch ready = new CountDownLatch(tasksCount);
    CountDownLatch start = new CountDownLatch(1);

    List<Future<Boolean>> futures = new ArrayList<>(tasksCount);
    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      for (int i = 0; i < tasksCount; i++) {
        futures.add(
          pool.submit(() -> {
            ready.countDown();
            start.await();

            for (int j = 0; j < 1024; j++) {
              boolean noneItemsSkipped = task.processAll(items, null);
              if (!noneItemsSkipped) {
                return false;
              }
            }
            return true;
          })
        );
      }

      // wait until both threads are ready, then let them go concurrently
      ready.await();
      start.countDown();

      boolean allSuccessful = true;
      for (Future<Boolean> future : futures) {
        allSuccessful = allSuccessful && future.get();
      }

      // At least one of the concurrent invocations should report that it skipped some items
      assertFalse(
        allSuccessful,
        "Expected at least one processAll() to return false when invoked in parallel on the same collection"
      );
    }
  }

  private static final class SlowProcessingTask extends UpdateTask<Integer> {
    @Override
    protected void doProcess(@NotNull Integer item,
                             @Nullable Project project) {
      // Intentionally slow down processing a bit to increase the overlap window between threads
      // The test doesn't rely on project being non-null
      try { Thread.sleep(10); } catch (InterruptedException ignored) {}
    }
  }

  /** Blocks the first processing attempt so that another invocation can deterministically contend for the same item. */
  private static final class BlockingProcessingTask<T> extends UpdateTask<T> {
    private final CountDownLatch firstProcessingStarted = new CountDownLatch(1);
    private final CountDownLatch releaseFirstProcessing = new CountDownLatch(1);
    private final AtomicInteger processingCount = new AtomicInteger();
    private final AtomicInteger activeProcessingCount = new AtomicInteger();
    private final AtomicInteger maxActiveProcessingCount = new AtomicInteger();

    @Override
    protected void doProcess(@NotNull T item, @Nullable Project project) {
      int active = activeProcessingCount.incrementAndGet();
      maxActiveProcessingCount.accumulateAndGet(active, Math::max);
      try {
        if (processingCount.incrementAndGet() == 1) {
          firstProcessingStarted.countDown();
          if (!releaseFirstProcessing.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("The test did not release the first processing attempt");
          }
        }
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError("Interrupted while waiting to release the first processing attempt", e);
      }
      finally {
        activeProcessingCount.decrementAndGet();
      }
    }
  }

  /** Tracks retries while blocking one item so an unrelated successful item can complete the first pass. */
  private static final class SelectivelyBlockingProcessingTask extends UpdateTask<Integer> {
    private final CountDownLatch blockingItemProcessingStarted = new CountDownLatch(1);
    private final CountDownLatch releaseBlockingItem = new CountDownLatch(1);
    private final CountDownLatch unrelatedItemProcessed = new CountDownLatch(1);
    private final ConcurrentMap<Integer, Integer> processingCounts = new ConcurrentHashMap<>();

    @Override
    protected void doProcess(@NotNull Integer item, @Nullable Project project) {
      int processingCount = processingCounts.merge(item, 1, Integer::sum);
      if (item == 1 && processingCount == 1) {
        blockingItemProcessingStarted.countDown();
        try {
          if (!releaseBlockingItem.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("The test did not release the blocking item");
          }
        }
        catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new AssertionError("Interrupted while waiting to release the blocking item", e);
        }
      }
      else if (item == 2) {
        unrelatedItemProcessed.countDown();
      }
    }

    private int processingCount(int item) {
      return processingCounts.getOrDefault(item, 0);
    }
  }

  /** Signals after the first item attempt, allowing the test to observe a skipped first pass without timing assumptions. */
  private static final class AttemptSignalingCollection<T> extends AbstractCollection<T> {
    private final CountDownLatch firstAttemptFinished;
    private final T item;

    private AttemptSignalingCollection(CountDownLatch firstAttemptFinished, T item) {
      this.firstAttemptFinished = firstAttemptFinished;
      this.item = item;
    }

    @Override
    public @NotNull Iterator<T> iterator() {
      return new Iterator<>() {
        private boolean itemAvailable = true;

        @Override
        public boolean hasNext() {
          if (!itemAvailable) {
            firstAttemptFinished.countDown();
          }
          return itemAvailable;
        }

        @Override
        public T next() {
          itemAvailable = false;
          return item;
        }
      };
    }

    @Override
    public int size() {
      return 1;
    }
  }

  /** Records sequential processing so the no-contention contract can be asserted directly. */
  private static final class RecordingTask extends UpdateTask<Integer> {
    private final List<Integer> processedItems = new CopyOnWriteArrayList<>();

    @Override
    protected void doProcess(@NotNull Integer item, @Nullable Project project) {
      processedItems.add(item);
    }
  }

  /** Coordinates processing entries shared by one or more task instances. */
  private static final class ConcurrentEntryTracker {
    private final CountDownLatch expectedEntries;
    private final AtomicInteger activeEntries = new AtomicInteger();
    private final AtomicInteger maxActiveEntries = new AtomicInteger();

    private ConcurrentEntryTracker(int expectedEntries) {
      this.expectedEntries = new CountDownLatch(expectedEntries);
    }

    private void enterAndAwaitOthers() {
      int active = activeEntries.incrementAndGet();
      maxActiveEntries.accumulateAndGet(active, Math::max);
      expectedEntries.countDown();
      try {
        if (!expectedEntries.await(10, TimeUnit.SECONDS)) {
          throw new AssertionError("Expected processing operations did not overlap");
        }
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError("Interrupted while waiting for concurrent processing", e);
      }
      finally {
        activeEntries.decrementAndGet();
      }
    }
  }

  /** Delegates processing to a shared tracker to verify which operations may overlap. */
  private static final class ConcurrentProcessingTask<T> extends UpdateTask<T> {
    private final ConcurrentEntryTracker tracker;

    private ConcurrentProcessingTask(ConcurrentEntryTracker tracker) {
      this.tracker = tracker;
    }

    @Override
    protected void doProcess(@NotNull T item, @Nullable Project project) {
      tracker.enterAndAwaitOthers();
    }
  }

  /** Fails its first attempt to verify that UpdateTask releases all coordination state on exceptions. */
  private static final class FailingOnceTask extends UpdateTask<Integer> {
    private final AtomicBoolean firstAttempt = new AtomicBoolean(true);
    private final AtomicInteger processingCount = new AtomicInteger();

    @Override
    protected void doProcess(@NotNull Integer item, @Nullable Project project) {
      processingCount.incrementAndGet();
      if (firstAttempt.compareAndSet(true, false)) {
        throw new TestProcessingException();
      }
    }
  }

  /** Reenters processAll once to verify that nested processing fails before waiting on its own outer operation. */
  private static final class ReentrantOnceTask extends UpdateTask<Integer> {
    private final AtomicBoolean firstAttempt = new AtomicBoolean(true);
    private final AtomicInteger processingCount = new AtomicInteger();

    @Override
    protected void doProcess(@NotNull Integer item, @Nullable Project project) {
      processingCount.incrementAndGet();
      if (firstAttempt.compareAndSet(true, false)) {
        processAll(List.of(item), project);
      }
    }
  }

  /** Distinguishes equal values from identical objects in the item-exclusion test. */
  private record EqualItem(int value) { }

  /** Identifies the intentional failure used to verify exception cleanup. */
  private static final class TestProcessingException extends RuntimeException { }
}
