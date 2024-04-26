// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.helpers

import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis

@ApiStatus.Internal
@JvmInline
value class Milliseconds(val value: Long) {
  companion object {
    fun now(): Milliseconds {
      return Milliseconds(System.currentTimeMillis())
    }
  }

  operator fun plus(other: Milliseconds): Milliseconds {
    return Milliseconds(this.value + other.value)
  }

  operator fun minus(other: Milliseconds): Milliseconds {
    return Milliseconds(this.value - other.value)
  }
}

@ApiStatus.Internal
@JvmInline
value class MillisecondsMeasurer(val duration: AtomicLong = AtomicLong()) : MillisecondsExportable {

  /**
   * Add NOW.minus([startTime]) in milliseconds to the current value
   * [startTimeMs] - start time of measurement in milliseconds
   */
  fun addElapsedTime(startTimeMs: Milliseconds): MillisecondsMeasurer {
    this.duration.addAndGet(System.currentTimeMillis() - startTimeMs.value)
    return this
  }

  /**
   * Measure time of the [block] in milliseconds and add it to current value.
   * @return Value [T], calculated by the [block]
   */
  inline fun <T> addMeasuredTime(block: () -> T): T {
    val value: T
    this.duration.addAndGet(measureTimeMillis { value = block() })
    return value
  }

  override fun asMilliseconds(): Long = duration.get()
}
