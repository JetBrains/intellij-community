// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class FlushingDaemon {
  public static final String NAME = "Flushing Daemon";

  private FlushingDaemon() {}

  @NotNull
  public static ScheduledFuture<?> everyFiveSeconds(@NotNull Runnable r) {
    return AppExecutorUtil
      .getAppScheduledExecutorService()
      .scheduleWithFixedDelay(ConcurrencyUtil.underThreadNameRunnable(NAME, r), 5, 5, TimeUnit.SECONDS);
  }
}
