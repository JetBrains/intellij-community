// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import kotlin.time.Duration.Companion.milliseconds

/**
 * The time sources of the process: the monotonic clock, the thread sleep, the coroutine delay, and the total GC time.
 *
 * A fake `sleep` or `delay` must advance the same clock that `nanoTime` reads.
 * Production code uses [SystemClock]. Tests replace it with a fake clock to simulate a process stall.
 */
internal interface ProcessClock {
  /** See [System.nanoTime]. */
  fun nanoTime(): Long

  /** Blocks the current thread for [ms] milliseconds. */
  fun sleep(ms: Long)

  /** Suspends the current coroutine for [ms] milliseconds. */
  suspend fun delay(ms: Long)

  /** The total GC time of the process, in milliseconds. */
  fun gcTimeMs(): Long
}

internal object SystemClock : ProcessClock {
  override fun nanoTime(): Long = System.nanoTime()

  override fun sleep(ms: Long) {
    Thread.sleep(ms)
  }

  override suspend fun delay(ms: Long) {
    kotlinx.coroutines.delay(ms.milliseconds)
  }

  override fun gcTimeMs(): Long = currentGcTime()
}
