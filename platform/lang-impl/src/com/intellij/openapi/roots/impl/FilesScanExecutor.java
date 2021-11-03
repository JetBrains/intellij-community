// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.Processor;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.indexing.UnindexedFilesUpdater;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@ApiStatus.Internal
public final class FilesScanExecutor {
  private static final int THREAD_COUNT = Math.max(UnindexedFilesUpdater.getNumberOfScanningThreads() - 1, 1);
  private static final ExecutorService ourExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Scanning", THREAD_COUNT);

  public static void runOnAllThreads(@NotNull Runnable runnable) {
    ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
    List<Future<?>> results = new ArrayList<>();
    for (int i = 0; i < THREAD_COUNT; i++) {
      results.add(ourExecutor.submit(() -> {
        ProgressManager.getInstance().runProcess(runnable, ProgressWrapper.wrap(progress));
      }));
    }
    // put the current thread to work too so the total thread count is `getNumberOfScanningThreads`
    // and avoid thread starvation due to a recursive `runOnAllThreads` invocation
    runnable.run();
    for (Future<?> result : results) {
      // complete the future to avoid waiting for it forever if `ourExecutor` is fully booked
      ((FutureTask<?>)result).run();
      ProgressIndicatorUtils.awaitWithCheckCanceled(result);
    }
  }

  public static <T> boolean processDequeOnAllThreads(@NotNull ConcurrentLinkedDeque<T> deque,
                                                     @NotNull Processor<? super T> processor) {
    ProgressManager.checkCanceled();
    if (deque.isEmpty()) return true;
    AtomicInteger runnersCount = new AtomicInteger();
    AtomicInteger idleCount = new AtomicInteger();
    AtomicReference<Throwable> error = new AtomicReference<>();
    AtomicBoolean stopped = new AtomicBoolean();
    AtomicBoolean exited = new AtomicBoolean();
    runOnAllThreads(() -> {
      runnersCount.incrementAndGet();
      boolean idle = false;
      while (idleCount.get() != runnersCount.get() && !stopped.get()) {
        ProgressManager.checkCanceled();
        if (deque.peek() == null) {
          if (!idle) {
            idle = true;
            idleCount.incrementAndGet();
          }
          TimeoutUtil.sleep(1L);
          continue;
        }
        else if (idle) {
          idle = false;
          idleCount.decrementAndGet();
        }

        T item = deque.poll();
        if (item == null) continue;

        try {
          if (!processor.process(item)) {
            stopped.set(true);
          }
          if (exited.get() && !stopped.get()) {
            throw new AssertionError("early exit");
          }
        }
        catch (ProcessCanceledException ex) {
          deque.addFirst(item);
        }
        catch (Throwable ex) {
          error.compareAndSet(null, ex);
        }
      }
      exited.set(true);
      if (!deque.isEmpty() && !stopped.get()) {
        throw new AssertionError("early exit");
      }
    });
    ExceptionUtil.rethrowAllAsUnchecked(error.get());
    return !stopped.get();
  }
}
