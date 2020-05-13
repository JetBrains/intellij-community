// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.concurrency;

import java.util.concurrent.ForkJoinPool;

public final class JobSchedulerImpl {
  /**
   * a number of CPU cores
   */
  public static int getCPUCoresCount() {
    return Runtime.getRuntime().availableProcessors();
  }

  /**
   * A number of concurrent threads in the thread pool the JobLauncher uses to execute tasks.
   * By default it's CORES_COUNT - 1, but can be adjusted
   * via "java.util.concurrent.ForkJoinPool.common.parallelism property", e.g. "-Djava.util.concurrent.ForkJoinPool.common.parallelism=8"
  */
  public static int getJobPoolParallelism() {
    return ForkJoinPool.getCommonPoolParallelism();
  }
}
