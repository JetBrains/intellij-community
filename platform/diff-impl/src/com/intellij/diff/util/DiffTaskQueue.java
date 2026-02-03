// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.util;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.BackgroundTaskUtil;
import com.intellij.util.Function;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class DiffTaskQueue {
  private @Nullable ProgressIndicator myProgressIndicator;

  @RequiresEdt
  public void abort() {
    if (myProgressIndicator != null) myProgressIndicator.cancel();
    myProgressIndicator = null;
  }

  @RequiresEdt
  public void executeAndTryWait(@NotNull Function<? super ProgressIndicator, ? extends Runnable> backgroundTask,
                                @Nullable Runnable onSlowAction,
                                long waitMillis) {
    executeAndTryWait(backgroundTask, onSlowAction, waitMillis, false);
  }

  @RequiresEdt
  public void executeAndTryWait(@NotNull Function<? super ProgressIndicator, ? extends Runnable> backgroundTask,
                                @Nullable Runnable onSlowAction,
                                long waitMillis,
                                boolean forceEDT) {
    abort();
    myProgressIndicator = BackgroundTaskUtil.executeAndTryWait(backgroundTask, onSlowAction, waitMillis, forceEDT);
  }
}
