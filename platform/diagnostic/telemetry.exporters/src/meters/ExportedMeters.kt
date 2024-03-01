// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.exporters.meters

import io.opentelemetry.sdk.metrics.data.DoublePointData
import io.opentelemetry.sdk.metrics.data.HistogramPointData
import io.opentelemetry.sdk.metrics.data.LongPointData
import io.opentelemetry.sdk.metrics.data.SummaryPointData
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class ExportedMeters(
  val counters: List<LongPointData>,
  val doubleCounters: List<DoublePointData>,
  val gauges: List<LongPointData>,
  val doubleGauges: List<DoublePointData>,
  val histograms: List<HistogramPointData>,
  // TODO: exponential histogram isn't supported yet
  // TODO: summary isn't supported yet
)