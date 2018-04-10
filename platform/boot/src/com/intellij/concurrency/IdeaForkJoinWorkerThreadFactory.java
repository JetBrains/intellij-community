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
  // must be called in the earliest possible moment on startup, but after Main.setFlags()
  public static void setupForkJoinCommonPool(boolean headless) {
    System.setProperty("java.util.concurrent.ForkJoinPool.common.threadFactory", IdeaForkJoinWorkerThreadFactory.class.getName());
    boolean parallelismWasNotSpecified = System.getProperty("java.util.concurrent.ForkJoinPool.common.parallelism") == null;
    if (parallelismWasNotSpecified) {
      int N_CPU = Runtime.getRuntime().availableProcessors();
      // By default FJP initialized with the parallelism=N_CPU - 1
      // so in case of two processors it becomes parallelism=1 which is too unexpected.
      // In this case force parallelism=2
      // In case of headless execution (unit tests or inspection command-line) there is no AWT thread to reserve cycles for, so dedicate all CPUs for FJP
      if (headless || N_CPU == 2) {
        System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", String.valueOf(N_CPU));
      }
    }
  }

  public static void setupPoisonFactory() {
    // if this method is called early enough and app called setupForkJoinCommonPool() too late
    // (setupForkJoinCommonPool() has to be called after Main.setFlags() but before FJP init)
    // then on the first use the FJP will explode, revealing who did the too early FJP init
    if (!IdeaForkJoinWorkerThreadFactory.class.getName().equals(System.getProperty("java.util.concurrent.ForkJoinPool.common.threadFactory"))) {
      System.setProperty("java.util.concurrent.ForkJoinPool.common.threadFactory", PoisonFactory.class.getName());
    }
  }

  private static final AtomicLong bits = new AtomicLong();
  @Override
  public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
    final int n = setNextBit();
    //System.out.println("New  FJP thread "+n);
    ForkJoinWorkerThread thread = new ForkJoinWorkerThread(pool) {
      @Override
      protected void onTermination(Throwable exception) {
        //System.out.println("Exit FJP thread "+n);
        clearBit(n);
        super.onTermination(exception);
      }
    };
    thread.setName("JobScheduler FJ pool " + n + "/" + pool.getParallelism());
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
