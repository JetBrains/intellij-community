// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ProgressWrapper;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.indexing.UnindexedFilesUpdater;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

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
    runnable.run();
    for (Future<?> result : results) {
      ProgressIndicatorUtils.awaitWithCheckCanceled(result);
    }
  }
}
