// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry

import com.intellij.util.concurrency.SynchronizedClearableLazy
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.metrics.InstrumentType
import io.opentelemetry.sdk.metrics.data.AggregationTemporality
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.export.MetricExporter
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class FilteredMetricsExporter(private val underlyingExporter: SynchronizedClearableLazy<MetricExporter>,
                              private val predicate: (MetricData) -> Boolean = { true }) : MetricExporter {
  override fun getAggregationTemporality(instrumentType: InstrumentType): AggregationTemporality {
    return underlyingExporter.value.getAggregationTemporality(instrumentType)
  }

  override fun export(metrics: MutableCollection<MetricData>): CompletableResultCode {
    val list = metrics.filter(predicate)
    return if (list.isEmpty()) CompletableResultCode.ofSuccess() else underlyingExporter.value.export(list)
  }

  override fun flush(): CompletableResultCode {
    return underlyingExporter.valueIfInitialized?.flush() ?: CompletableResultCode.ofSuccess()
  }

  override fun shutdown(): CompletableResultCode {
    return underlyingExporter.valueIfInitialized?.shutdown() ?: CompletableResultCode.ofSuccess()
  }
}