// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.helpers

import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis

/**
 * Add now-startTime in ms to the current value
 * [startTimeMs] - start time of measurement in milliseconds
 */
fun AtomicLong.addElapsedTimeMs(startTimeMs: Long) {
  this.addAndGet(System.currentTimeMillis() - startTimeMs)
}

/**
 * Measure time of the [block] in milliseconds and add it to current value.
 * @return Value [T], calculated by the [block]
 */
inline fun <T> AtomicLong.addMeasuredTimeMs(block: () -> T): T {
  val value: T
  this.addAndGet(measureTimeMillis { value = block() })
  return value
}