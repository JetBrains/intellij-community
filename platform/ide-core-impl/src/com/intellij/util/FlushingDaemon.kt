// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class FlushingDaemon {
  public static final String NAME = "Flushing Daemon";

  @ApiStatus.Internal
  public static final long FLUSHING_PERIOD_IN_SECONDS = 1;

  private FlushingDaemon() {}

  @NotNull
  public static ScheduledFuture<?> runPeriodically(@NotNull Runnable r) {
    return AppExecutorUtil
      .getAppScheduledExecutorService()
      .scheduleWithFixedDelay(ConcurrencyUtil.underThreadNameRunnable(NAME, r),
                              FLUSHING_PERIOD_IN_SECONDS,
                              FLUSHING_PERIOD_IN_SECONDS,
                              TimeUnit.SECONDS);
  }
}
