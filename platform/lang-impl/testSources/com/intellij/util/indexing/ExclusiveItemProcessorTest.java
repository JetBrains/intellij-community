// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.testFramework.junit5.TestApplication;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that when the same {@link ExclusiveItemProcessor} instance processes the same collection from different
 * threads in parallel, at least one invocation of {@link ExclusiveItemProcessor#processAll(Collection, Project)}
 * reports that some items were skipped (returns {@code false}).
 */
@TestApplication
public class ExclusiveItemProcessorTest {
  /** Supplies the non-null project required by ExclusiveItemProcessor without introducing a project fixture unused by these tests. */
  private static @NotNull Project project() {
    return ProjectManager.getInstance().getDefaultProject();
  }

  /** Verifies deterministically that contention is reported even though the skipped item is eventually retried. */
  @Test
  public void processAllReturnsFalseAfterSkippedItemIsRetried() throws Exception {
    BlockingProcessor<Integer> processor = new BlockingProcessor<>();
    ExclusiveItemProcessor<Integer> exclusiveProcessor = new ExclusiveItemProcessor<>(processor::process);
    CountDownLatch competingAttemptFinished = new CountDownLatch(1);

    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      Future<Boolean> firstResult = pool.submit(() -> exclusiveProcessor.processAll(List.of(1), project()));
      assertTrue(processor.firstProcessingStarted.await(10, TimeUnit.SECONDS), "The first processing attempt did not start");

      Future<Boolean> competingResult = pool.submit(
        () -> exclusiveProcessor.processAll(new AttemptSignalingCollection<>(competingAttemptFinished, 1), project())
      );
      try {
        assertTrue(competingAttemptFinished.await(10, TimeUnit.SECONDS), "The competing attempt did not reach the busy item");
      }
      finally {
        processor.releaseFirstProcessing.countDown();
      }

      assertTrue(firstResult.get());
      assertFalse(competingResult.get(), "A call that skipped an item must report contention after retrying it");
      assertEquals(2, processor.processingCount.get(), "Each invocation must eventually process the item once");
    }
  }

  /** Verifies that a retry pass contains only items skipped because of concurrent processing. */
  @Test
  public void onlyItemsSkippedDueToContentionAreRetried() throws Exception {
    SelectivelyBlockingProcessor processor = new SelectivelyBlockingProcessor();
    ExclusiveItemProcessor<Integer> exclusiveProcessor = new ExclusiveItemProcessor<>(processor::process);

    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      Future<Boolean> blockingResult = pool.submit(() -> exclusiveProcessor.processAll(List.of(1), project()));
      assertTrue(processor.blockingItemProcessingStarted.await(10, TimeUnit.SECONDS), "The blocking item was not processed");

      Future<Boolean> competingResult = pool.submit(() -> exclusiveProcessor.processAll(List.of(1, 2), project()));
      try {
        assertTrue(processor.unrelatedItemProcessed.await(10, TimeUnit.SECONDS), "The unrelated item was not processed on the first pass");
      }
      finally {
        processor.releaseBlockingItem.countDown();
      }

      assertTrue(blockingResult.get());
      assertFalse(competingResult.get());
      assertEquals(2, processor.processingCount(1), "The contended item must be retried");
      assertEquals(1, processor.processingCount(2), "An item processed successfully on the first pass must not be retried");
    }
  }

  /** Verifies the basic successful contract without concurrent invocations. */
  @Test
  public void processAllReturnsTrueWithoutContention() {
    List<Integer> processedItems = new ArrayList<>();
    ExclusiveItemProcessor<Integer> exclusiveProcessor = new ExclusiveItemProcessor<>((item, ignoredProject) -> processedItems.add(item));

    assertTrue(exclusiveProcessor.processAll(List.of(1, 2), project()));
    assertEquals(List.of(1, 2), processedItems, "Every item must be processed exactly once without contention");
  }

  /** Verifies that the processor coordinates equal items without serializing unrelated ones. */
  @Test
  public void differentItemsAreProcessedConcurrently() throws Exception {
    ConcurrentProcessor<Integer> processor = new ConcurrentProcessor<>(2);
    ExclusiveItemProcessor<Integer> exclusiveProcessor = new ExclusiveItemProcessor<>(processor::process);

    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      Future<Boolean> firstResult = pool.submit(() -> exclusiveProcessor.processAll(List.of(1), project()));
      Future<Boolean> secondResult = pool.submit(() -> exclusiveProcessor.processAll(List.of(2), project()));

      assertTrue(firstResult.get());
      assertTrue(secondResult.get());
      assertEquals(2, processor.maxActiveEntries.get(), "Different items must not be serialized by the processor semaphore");
    }
  }

  /** Verifies that exceptional processing does not leave the item occupied or the semaphore unbalanced. */
  @Test
  public void exceptionDoesNotLeakItemOrSemaphorePermit() {
    AtomicBoolean firstAttempt = new AtomicBoolean(true);
    AtomicInteger processingCount = new AtomicInteger();
    ExclusiveItemProcessor<Integer> exclusiveProcessor = new ExclusiveItemProcessor<>((ignoredItem, ignoredProject) -> {
      processingCount.incrementAndGet();
      if (firstAttempt.compareAndSet(true, false)) {
        throw new TestProcessingException();
      }
    });

    assertThrows(TestProcessingException.class, () -> exclusiveProcessor.processAll(List.of(1), project()));
    assertTrue(exclusiveProcessor.processAll(List.of(1), project()));
    assertEquals(2, processingCount.get(), "The item must remain processable after an exception");
  }

  /** Verifies that reentrancy fails fast and releases all state held by the outer invocation. */
  @Test
  public void reentrantProcessAllFailsFastAndDoesNotCorruptProcessor() {
    AtomicReference<ExclusiveItemProcessor<Integer>> processorRef = new AtomicReference<>();
    AtomicBoolean firstAttempt = new AtomicBoolean(true);
    AtomicInteger processingCount = new AtomicInteger();
    ExclusiveItemProcessor<Integer> exclusiveProcessor = new ExclusiveItemProcessor<>((item, project) -> {
      processingCount.incrementAndGet();
      if (firstAttempt.compareAndSet(true, false)) {
        processorRef.get().processAll(List.of(item), project);
      }
    });
    processorRef.set(exclusiveProcessor);

    assertThrows(IllegalStateException.class, () -> exclusiveProcessor.processAll(List.of(1), project()));
    assertTrue(exclusiveProcessor.processAll(List.of(1), project()), "The processor must remain usable after a rejected reentrant call");
    assertEquals(2, processingCount.get(), "The outer failed attempt must release item ownership");
  }

  /** Verifies directly that the same item is never passed to two processor calls concurrently. */
  @Test
  public void sameItemIsNeverProcessedConcurrently() throws Exception {
    BlockingProcessor<Integer> processor = new BlockingProcessor<>();
    ExclusiveItemProcessor<Integer> exclusiveProcessor = new ExclusiveItemProcessor<>(processor::process);
    CountDownLatch competingAttemptFinished = new CountDownLatch(1);

    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      Future<Boolean> firstResult = pool.submit(() -> exclusiveProcessor.processAll(List.of(1), project()));
      assertTrue(processor.firstProcessingStarted.await(10, TimeUnit.SECONDS), "The first processing attempt did not start");

      Future<Boolean> competingResult = pool.submit(
        () -> exclusiveProcessor.processAll(new AttemptSignalingCollection<>(competingAttemptFinished, 1), project())
      );
      try {
        assertTrue(competingAttemptFinished.await(10, TimeUnit.SECONDS), "The competing attempt did not reach the busy item");
        assertEquals(1, processor.maxActiveProcessingCount.get(), "Equal items must not be processed concurrently");
      }
      finally {
        processor.releaseFirstProcessing.countDown();
      }

      assertTrue(firstResult.get());
      assertFalse(competingResult.get());
      assertEquals(1, processor.maxActiveProcessingCount.get(), "Equal items must remain serialized during retry");
    }
  }

  /** Verifies that item exclusion is local to an ExclusiveItemProcessor instance rather than global. */
  @Test
  public void differentProcessorInstancesDoNotCoordinate() throws Exception {
    ConcurrentProcessor<Integer> processor = new ConcurrentProcessor<>(2);
    ExclusiveItemProcessor<Integer> firstExclusiveProcessor = new ExclusiveItemProcessor<>(processor::process);
    ExclusiveItemProcessor<Integer> secondExclusiveProcessor = new ExclusiveItemProcessor<>(processor::process);

    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      Future<Boolean> firstResult = pool.submit(() -> firstExclusiveProcessor.processAll(List.of(1), project()));
      Future<Boolean> secondResult = pool.submit(() -> secondExclusiveProcessor.processAll(List.of(1), project()));

      assertTrue(firstResult.get());
      assertTrue(secondResult.get());
      assertEquals(2, processor.maxActiveEntries.get(), "Different processor instances must not share item ownership");
    }
  }

  /** Verifies that item exclusion follows equals/hashCode rather than object identity. */
  @Test
  public void equalItemsAreConsideredTheSame() throws Exception {
    BlockingProcessor<EqualItem> processor = new BlockingProcessor<>();
    ExclusiveItemProcessor<EqualItem> exclusiveProcessor = new ExclusiveItemProcessor<>(processor::process);
    CountDownLatch competingAttemptFinished = new CountDownLatch(1);
    EqualItem firstItem = new EqualItem(1);
    EqualItem equalItem = new EqualItem(1);

    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      Future<Boolean> firstResult = pool.submit(() -> exclusiveProcessor.processAll(List.of(firstItem), project()));
      assertTrue(processor.firstProcessingStarted.await(10, TimeUnit.SECONDS), "The first processing attempt did not start");

      Future<Boolean> competingResult = pool.submit(
        () -> exclusiveProcessor.processAll(new AttemptSignalingCollection<>(competingAttemptFinished, equalItem), project())
      );
      try {
        assertTrue(competingAttemptFinished.await(10, TimeUnit.SECONDS), "The equal item did not contend with the occupied item");
      }
      finally {
        processor.releaseFirstProcessing.countDown();
      }

      assertTrue(firstResult.get());
      assertFalse(competingResult.get(), "A distinct but equal item must be reported as skipped before retry");
      assertEquals(1, processor.maxActiveProcessingCount.get(), "Distinct equal items must not be processed concurrently");
    }
  }

  /** Verifies that cancellation during semaphore waiting leaves the processor reusable. */
  @Test
  public void cancellationWhileWaitingDoesNotCorruptProcessor() throws Exception {
    BlockingProcessor<Integer> processor = new BlockingProcessor<>();
    ExclusiveItemProcessor<Integer> exclusiveProcessor = new ExclusiveItemProcessor<>(processor::process);
    CountDownLatch competingAttemptFinished = new CountDownLatch(1);
    EmptyProgressIndicator indicator = new EmptyProgressIndicator(ModalityState.nonModal());

    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      Future<Boolean> firstResult = pool.submit(() -> exclusiveProcessor.processAll(List.of(1), project()));
      assertTrue(processor.firstProcessingStarted.await(10, TimeUnit.SECONDS), "The first processing attempt did not start");

      Future<Boolean> canceledResult = pool.submit(() -> ProgressManager.getInstance().runProcess(
        () -> exclusiveProcessor.processAll(new AttemptSignalingCollection<>(competingAttemptFinished, 1), project()),
        indicator
      ));
      try {
        assertTrue(competingAttemptFinished.await(10, TimeUnit.SECONDS), "The cancellable attempt did not reach the busy item");
        indicator.cancel();
        ExecutionException exception = assertThrows(ExecutionException.class, () -> canceledResult.get(10, TimeUnit.SECONDS));
        assertInstanceOf(ProcessCanceledException.class, exception.getCause());
      }
      finally {
        processor.releaseFirstProcessing.countDown();
      }

      assertTrue(firstResult.get());
      assertTrue(exclusiveProcessor.processAll(List.of(1), project()), "The processor must remain usable after a waiting invocation is canceled");
    }
  }

  @Test
  public void processAllReturnsFalseSometimesWhenCalledInParallelOnSameCollection() throws Exception {
    ExclusiveItemProcessor<Integer> exclusiveProcessor = new ExclusiveItemProcessor<>((ignoredItem, ignoredProject) -> {
      // Intentionally slow down processing a bit to increase the overlap window between threads.
      try {
        Thread.sleep(10);
      }
      catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError("Interrupted while slowing down item processing", e);
      }
    });

    // Use a small collection to maximize contention on the very first items
    Collection<Integer> items = List.of(1, 2, 3, 4, 5);

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
              boolean noneItemsSkipped = exclusiveProcessor.processAll(items, project());
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

  /** Blocks the first processing attempt so that another invocation can deterministically contend for the same item. */
  private static final class BlockingProcessor<T> {
    private final CountDownLatch firstProcessingStarted = new CountDownLatch(1);
    private final CountDownLatch releaseFirstProcessing = new CountDownLatch(1);
    private final AtomicInteger processingCount = new AtomicInteger();
    private final AtomicInteger activeProcessingCount = new AtomicInteger();
    private final AtomicInteger maxActiveProcessingCount = new AtomicInteger();

    /** Blocks only the first invocation so a competing ExclusiveItemProcessor call can observe the occupied item. */
    private void process(@NotNull T ignoredItem, @NotNull Project ignoredProject) {
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
  private static final class SelectivelyBlockingProcessor {
    private final CountDownLatch blockingItemProcessingStarted = new CountDownLatch(1);
    private final CountDownLatch releaseBlockingItem = new CountDownLatch(1);
    private final CountDownLatch unrelatedItemProcessed = new CountDownLatch(1);
    private final ConcurrentMap<Integer, Integer> processingCounts = new ConcurrentHashMap<>();

    /** Blocks the first processing of item 1 while allowing item 2 to signal its completion. */
    private void process(@NotNull Integer item, @NotNull Project ignoredProject) {
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
    private final List<T> items;

    private AttemptSignalingCollection(CountDownLatch firstAttemptFinished, T item) {
      this(firstAttemptFinished, List.of(item));
    }

    private AttemptSignalingCollection(CountDownLatch firstAttemptFinished, List<T> items) {
      this.firstAttemptFinished = firstAttemptFinished;
      this.items = items;
    }

    @Override
    public @NotNull Iterator<T> iterator() {
      Iterator<T> delegate = items.iterator();
      return new Iterator<>() {
        @Override
        public boolean hasNext() {
          boolean hasNext = delegate.hasNext();
          if (!hasNext) {
            firstAttemptFinished.countDown();
          }
          return hasNext;
        }

        @Override
        public T next() {
          return delegate.next();
        }
      };
    }

    @Override
    public int size() {
      return items.size();
    }
  }

  /** Coordinates processor entries shared by one or more ExclusiveItemProcessor instances. */
  private static final class ConcurrentProcessor<T> {
    private final CountDownLatch expectedEntries;
    private final AtomicInteger activeEntries = new AtomicInteger();
    private final AtomicInteger maxActiveEntries = new AtomicInteger();

    private ConcurrentProcessor(int expectedEntries) {
      this.expectedEntries = new CountDownLatch(expectedEntries);
    }

    /** Keeps each processor invocation active until the expected number of concurrent invocations have entered. */
    private void process(@NotNull T ignoredItem, @NotNull Project ignoredProject) {
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

  /** Distinguishes equal values from identical objects in the item-exclusion test. */
  private record EqualItem(int value) { }

  /** Identifies the intentional failure used to verify exception cleanup. */
  private static final class TestProcessingException extends RuntimeException { }
}
