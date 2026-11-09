// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.concurrency;

import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import java.util.BitSet;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;

@Internal
// must be accessible via "ClassLoader.getSystemClassLoader().loadClass(fp).newInstance()" from java.util.concurrent.ForkJoinPool.makeCommonPool()
public final class IdeaForkJoinWorkerThreadFactory implements ForkJoinPool.ForkJoinWorkerThreadFactory {
  // must be called in the earliest possible moment on startup, but after Main.setFlags()
  public static void setupForkJoinCommonPool(boolean headless) {
    System.setProperty("java.util.concurrent.ForkJoinPool.common.threadFactory", IdeaForkJoinWorkerThreadFactory.class.getName());
    boolean parallelismWasNotSpecified = System.getProperty("java.util.concurrent.ForkJoinPool.common.parallelism") == null;
    if (parallelismWasNotSpecified) {
      int N_CPU = Runtime.getRuntime().availableProcessors();
      // By default, FJP initialized with the parallelism=N_CPU - 1
      // so in case of two processors it becomes parallelism=1 which is too unexpected.
      // In this case force parallelism=2
      // In case of headless execution (unit tests or inspection command-line) there is no AWT thread to reserve cycles for, so dedicate all CPUs for FJP
      if (headless || N_CPU == 2) {
        System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", String.valueOf(N_CPU));
      }
    }
  }

  private static final BitSet threadNumbers = new BitSet();

  @Override
  public @NotNull ForkJoinWorkerThread newThread(ForkJoinPool pool) {
    int number;
    synchronized (threadNumbers) {
      number = threadNumbers.nextClearBit(0);
      threadNumbers.set(number);
    }
    ForkJoinWorkerThread thread = new ForkJoinWorkerThread(pool) {
      @Override
      protected void onTermination(Throwable exception) {
        synchronized (threadNumbers) {
          threadNumbers.clear(number);
        }
        super.onTermination(exception);
      }
    };
    thread.setName("JobScheduler FJ pool " + number + "/" + pool.getParallelism());
    return thread;
  }
}
