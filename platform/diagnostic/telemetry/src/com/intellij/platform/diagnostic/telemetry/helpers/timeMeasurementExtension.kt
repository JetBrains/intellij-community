// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.helpers

import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

/**
 * Add now.minus([startTime]) in milliseconds to the current value
 * [startTimeMs] - start time of measurement in milliseconds
 */
fun AtomicLong.addElapsedTimeMillis(startTimeMs: Long): Unit {
  this.addAndGet(System.currentTimeMillis() - startTimeMs)
}

/**
 * Add now.minus([startTime]) in nanoseconds to the current value
 * [startTimeNanosec] - start time of measurement in nanoseconds
 */
fun AtomicLong.addElapsedTimeNanosec(startTimeNanosec: Long): Unit {
  this.addAndGet(System.nanoTime() - startTimeNanosec)
}

/**
 * Measure time of the [block] in milliseconds and add it to current value.
 * @return Value [T], calculated by the [block]
 */
inline fun <T> AtomicLong.addMeasuredTimeMillis(block: () -> T): T {
  val value: T
  this.addAndGet(measureTimeMillis { value = block() })
  return value
}

/**
 * Measure time of the [block] in nanoseconds and add it to current value.
 * @return Value [T], calculated by the [block]
 */
inline fun <T> AtomicLong.addMeasuredTimeNanosec(block: () -> T): T {
  val value: T
  this.addAndGet(measureNanoTime { value = block() })
  return value
}

fun AtomicLong.fromNanosecToMillis(): Long = this.get() / 1_000_000