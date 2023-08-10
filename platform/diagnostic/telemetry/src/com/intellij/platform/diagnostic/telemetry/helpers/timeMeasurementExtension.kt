// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.helpers

import java.util.concurrent.atomic.AtomicLong
import kotlin.system.measureTimeMillis

/**
 * Add now-startTime in ms to the current value
 * [startTime] - start time of measurement in milliseconds
 */
fun AtomicLong.addElapsedTimeMs(startTime: Long) {
  this.addAndGet(System.currentTimeMillis() - startTime)
}

/**
 * Measure time in milliseconds and add it to current value
 */
inline fun AtomicLong.addMeasuredTimeMs(block: () -> Unit) {
  this.addAndGet(measureTimeMillis { block() })
}