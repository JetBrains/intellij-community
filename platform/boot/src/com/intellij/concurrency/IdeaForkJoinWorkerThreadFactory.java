/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.concurrency;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicLong;

// must be accessible via "ClassLoader.getSystemClassLoader().loadClass(fp).newInstance()" from java.util.concurrent.ForkJoinPool.makeCommonPool()
public class IdeaForkJoinWorkerThreadFactory implements ForkJoinPool.ForkJoinWorkerThreadFactory {
  private static final int PARALLELISM = Runtime.getRuntime().availableProcessors();

  // must be called in the earliest possible moment on startup
  public static void setupForkJoinCommonPool() {
    System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", String.valueOf(PARALLELISM));
    System.setProperty("java.util.concurrent.ForkJoinPool.common.threadFactory", IdeaForkJoinWorkerThreadFactory.class.getName());
  }

  private static final AtomicLong bits = new AtomicLong();
  @Override
  public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
    final int n = setNextBit();
    ForkJoinWorkerThread thread = new ForkJoinWorkerThread(pool) {
      @Override
      protected void onTermination(Throwable exception) {
        clearBit(n);
        super.onTermination(exception);
      }
    };
    thread.setName("JobScheduler FJ pool " + n + "/" + PARALLELISM);
    thread.setPriority(Thread.NORM_PRIORITY - 1);
    return thread;
  }

  private static int setNextBit() {
    long oldValue = bits.getAndUpdate(value -> value + 1 | value);
    return Long.numberOfTrailingZeros(oldValue + 1);
  }

  private static void clearBit(int n) {
    bits.updateAndGet(value -> value & ~(1L << n));
  }
}
