// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.aether;

import org.eclipse.aether.named.NamedLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FairLocalReadWriteLockNamedLockFactoryTest {
  @Test
  @Timeout(10)
  void queuedWriterAcquiresLockBeforeLaterReader() throws Exception {
    FairLocalReadWriteLockNamedLockFactory factory = new FairLocalReadWriteLockNamedLockFactory();
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch releaseWriter = new CountDownLatch(1);
    CountDownLatch writerAcquired = new CountDownLatch(1);
    CountDownLatch readerAcquired = new CountDownLatch(1);
    AtomicReference<Thread> writerThread = new AtomicReference<>();
    List<String> acquisitionOrder = new CopyOnWriteArrayList<>();

    try (NamedLock initialReader = factory.getLock("artifact")) {
      assertTrue(initialReader.lockShared(1, TimeUnit.SECONDS));

      Future<?> writer = executor.submit(() -> {
        writerThread.set(Thread.currentThread());
        try (NamedLock lock = factory.getLock("artifact")) {
          assertTrue(lock.lockExclusively(5, TimeUnit.SECONDS));
          acquisitionOrder.add("writer");
          writerAcquired.countDown();
          assertTrue(releaseWriter.await(5, TimeUnit.SECONDS));
          lock.unlock();
        }
        return null;
      });
      awaitLockWait(writerThread);

      Future<?> reader = executor.submit(() -> {
        try (NamedLock lock = factory.getLock("artifact")) {
          assertTrue(lock.lockShared(5, TimeUnit.SECONDS));
          acquisitionOrder.add("reader");
          readerAcquired.countDown();
          lock.unlock();
        }
        return null;
      });

      initialReader.unlock();
      assertTrue(writerAcquired.await(5, TimeUnit.SECONDS));
      assertEquals(List.of("writer"), acquisitionOrder);
      releaseWriter.countDown();
      assertTrue(readerAcquired.await(5, TimeUnit.SECONDS));
      writer.get();
      reader.get();
      assertEquals(List.of("writer", "reader"), acquisitionOrder);
    }
    finally {
      releaseWriter.countDown();
      executor.shutdownNow();
      factory.shutdown();
    }
  }

  private static void awaitLockWait(AtomicReference<Thread> threadReference) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
    while (System.nanoTime() < deadline) {
      Thread thread = threadReference.get();
      if (thread != null && (thread.getState() == Thread.State.WAITING || thread.getState() == Thread.State.TIMED_WAITING)) {
        return;
      }
      Thread.onSpinWait();
    }
    throw new AssertionError("Writer did not start waiting for the lock");
  }
}
