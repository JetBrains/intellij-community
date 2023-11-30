// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

@Deprecated("Use coroutines and execute task in a corresponding service coroutine scope")
object FlushingDaemon {
  const val NAME: String = "Flushing Daemon"

  @ApiStatus.Internal
  const val FLUSHING_PERIOD_IN_SECONDS: Long = 1

  @JvmStatic
  fun runPeriodically(r: Runnable): ScheduledFuture<*> {
    return AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay(ConcurrencyUtil.underThreadNameRunnable(NAME, r),
                                                                                   FLUSHING_PERIOD_IN_SECONDS,
                                                                                   FLUSHING_PERIOD_IN_SECONDS,
                                                                                   TimeUnit.SECONDS)
  }
}
