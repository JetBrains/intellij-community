// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.openapi.diagnostic.logger
import com.intellij.util.ConcurrencyUtil
import java.lang.management.ManagementFactory
import java.util.concurrent.TimeUnit

private val LOG = logger<ProcessStallWatcher>()

/**
 * One thread that sleeps [sliceMs] and checks how long it slept.
 *
 * A late wake-up is a process stall: a JVM pause, a host overload, or a `SIGSTOP`.
 * Every [slicesPerTick] sleeps the watcher sums the lateness. When the sum reaches [reportThresholdMs], it logs one warning.
 *
 * A late wake-up of this thread alone, for example under host CPU contention, also counts as a stall, by design.
 * The thread is the canary for the whole process.
 *
 * @param sliceMs the duration of one sleep in milliseconds
 * @param slicesPerTick the number of sleeps in one tick
 * @param reportThresholdMs the summed lateness in one tick, in milliseconds, that triggers the warning
 * @param clock the time sources; tests replace it with a fake clock to simulate a stall
 * @param warn writes the process stall warning; tests replace it to capture the message
 */
internal class ProcessStallWatcher(
  private val sliceMs: Long = 250,
  private val slicesPerTick: Int = 4,
  reportThresholdMs: Long = 1000,
  private val clock: ProcessClock = SystemClock,
  private val warn: (String) -> Unit = { LOG.warn(it) },
) {
  private val reportThresholdNs = TimeUnit.MILLISECONDS.toNanos(reportThresholdMs)

  private var lastGcTimeMs = clock.gcTimeMs()

  private val executor = ConcurrencyUtil.newSingleThreadExecutor("Process stall watcher")

  fun start() {
    LOG.debug("process stall watcher started")
    executor.execute {
      try {
        while (!Thread.currentThread().isInterrupted) {
          tick()
        }
      }
      catch (_: InterruptedException) {
        // expected: executor.shutdownNow() interrupts this thread on scope cancellation
      }
      finally {
        LOG.debug("process stall watcher stopped")
      }
    }
  }

  fun shutdown() {
    executor.shutdownNow()
  }

  /** One tick: [slicesPerTick] sleeps, then at most one warning. Called by the thread; visible for tests. */
  internal fun tick() {
    var lateInTickNs = 0L
    repeat(slicesPerTick) {
      val start = clock.nanoTime()
      clock.sleep(sliceMs)
      val lateNs = clock.nanoTime() - start - TimeUnit.MILLISECONDS.toNanos(sliceMs)
      lateInTickNs += lateNs.coerceAtLeast(0)
    }

    val gcTimeMs = clock.gcTimeMs()
    val gcDeltaMs = gcTimeMs - lastGcTimeMs
    lastGcTimeMs = gcTimeMs

    if (lateInTickNs >= reportThresholdNs) {
      val lateMs = TimeUnit.NANOSECONDS.toMillis(lateInTickNs)
      warn("Process stall: the watcher thread woke up $lateMs ms late " +
           "over the last ${sliceMs * slicesPerTick} ms of requested sleep " +
           "(GC time: $gcDeltaMs ms, system load average: ${systemLoadAverage()})")
    }
  }
}

private fun systemLoadAverage(): String {
  val loadAverage = ManagementFactory.getOperatingSystemMXBean().systemLoadAverage
  return if (loadAverage < 0) "n/a" else String.format("%.2f", loadAverage)
}
