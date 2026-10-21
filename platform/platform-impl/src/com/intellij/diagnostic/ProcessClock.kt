// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

/**
 * The time sources of the process: the monotonic clock, the thread sleep, and the total GC time.
 *
 * A fake `sleep` must advance the same clock that `nanoTime` reads.
 * Production code uses [SystemClock]. Tests replace it with a fake clock to simulate a process stall.
 */
internal interface ProcessClock {
  /** See [System.nanoTime]. */
  fun nanoTime(): Long

  /** Blocks the current thread for [ms] milliseconds. */
  fun sleep(ms: Long)

  /** The total GC time of the process, in milliseconds. */
  fun gcTimeMs(): Long
}

internal object SystemClock : ProcessClock {
  override fun nanoTime(): Long = System.nanoTime()

  override fun sleep(ms: Long) {
    Thread.sleep(ms)
  }

  override fun gcTimeMs(): Long = currentGcTime()
}
