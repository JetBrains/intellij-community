// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import kotlin.time.ComparableTimeMark
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * The time sources of the process: the monotonic clock, the thread sleep, the coroutine delay, and the total GC time.
 *
 * A fake `sleep` or `delay` must advance the same clock that `now` reads.
 * Production code uses [SystemClock]. Tests replace it with a fake clock to simulate a process stall.
 */
internal interface ProcessClock {
  /** The current point of the monotonic clock. Compare it only with the points of the same clock. */
  fun now(): ComparableTimeMark

  /** Blocks the current thread for [duration]. */
  fun sleep(duration: Duration)

  /** Suspends the current coroutine for [duration]. */
  suspend fun delay(duration: Duration)

  /** The total GC time of the process. */
  fun gcTime(): Duration
}

internal object SystemClock : ProcessClock {
  override fun now(): ComparableTimeMark = TimeSource.Monotonic.markNow()

  override fun sleep(duration: Duration) {
    Thread.sleep(duration.inWholeMilliseconds)
  }

  override suspend fun delay(duration: Duration) {
    kotlinx.coroutines.delay(duration)
  }

  override fun gcTime(): Duration = currentGcTime().milliseconds
}
