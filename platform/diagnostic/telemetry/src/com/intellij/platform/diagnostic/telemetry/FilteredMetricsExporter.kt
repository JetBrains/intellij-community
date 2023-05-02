// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry

import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.metrics.InstrumentType
import io.opentelemetry.sdk.metrics.data.AggregationTemporality
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.export.MetricExporter

class FilteredMetricsExporter(private val underlyingExporter: MetricExporter,
                              private val predicate: (MetricData) -> Boolean = { true }) : MetricExporter {
  override fun getAggregationTemporality(instrumentType: InstrumentType): AggregationTemporality {
    return underlyingExporter.getAggregationTemporality(instrumentType)
  }

  override fun export(metrics: MutableCollection<MetricData>): CompletableResultCode {
    return underlyingExporter.export(metrics.filter(predicate))
  }

  override fun flush(): CompletableResultCode {
    return underlyingExporter.flush()
  }

  override fun shutdown(): CompletableResultCode {
    return underlyingExporter.shutdown()
  }
}