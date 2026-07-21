// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.UI
import com.jetbrains.rd.util.ConcurrentHashMap
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.LongHistogram
import org.jetbrains.annotations.ApiStatus
import kotlin.time.TimeMark
import kotlin.time.measureTimedValue

@ApiStatus.Internal
class PluginManagerUiTracker {

  private val meter by lazy { TelemetryManager.getMeter(UI) }
  private val keyToHistogram: MutableMap<String, LongHistogram> = ConcurrentHashMap()
  private val keyToCounter: MutableMap<String, LongCounter> = ConcurrentHashMap()

  fun measure(name: String, timeMs: Long) {
    keyToHistogram.computeIfAbsent(getMetricName(name)) {
      meter.histogramBuilder(it)
        .setUnit("ms")
        .ofLongs()
        .build()
    }.record(timeMs)
  }

  fun measure(name: String, start: TimeMark) {
    measure(name, start.elapsedNow().inWholeMilliseconds)
  }

  fun <T> measure(name: String, compute: () -> T): T {
    val (result, timeSpent) = measureTimedValue {
      compute()
    }
    measure(name, timeSpent.inWholeMilliseconds)
    return result
  }

  fun logEvent(name: String) {
    keyToCounter.computeIfAbsent(getMetricName(name)) {
      meter.counterBuilder(it).build()
    }.add(1)
  }

  private fun getMetricName(name: String): String {
    return "plugin.manager.ui.$name"
  }
}
