// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.helpers

import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureNanoTime

@ApiStatus.Internal
@JvmInline
value class Nanoseconds(val value: Long) {
  companion object {
    fun now(): Nanoseconds {
      return Nanoseconds(System.nanoTime())
    }
  }

  operator fun plus(other: Nanoseconds): Nanoseconds {
    return Nanoseconds(this.value + other.value)
  }

  operator fun minus(other: Nanoseconds): Nanoseconds {
    return Nanoseconds(this.value - other.value)
  }
}

@ApiStatus.Internal
@JvmInline
value class NanosecondsMeasurer(val duration: AtomicLong = AtomicLong()) : MillisecondsExportable {
  /**
   * Add NOW.minus([startTimeNanosec]) in nanoseconds to the current value
   * [startTimeNanosec] - start time of measurement in nanoseconds
   */
  fun addElapsedTime(startTimeNanosec: Nanoseconds): NanosecondsMeasurer {
    this.duration.addAndGet(System.nanoTime() - startTimeNanosec.value)
    return this
  }

  /**
   * Measure time of the [block] in nanoseconds and add it to current value.
   * @return Value [T], calculated by the [block]
   */
  inline fun <T> addMeasuredTime(block: () -> T): T {
    val value: T
    this.duration.addAndGet(measureNanoTime { value = block() })
    return value
  }

  override fun asMilliseconds(): Long = this.duration.get() / 1_000_000
}
