// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.exporters.meters.models

import com.fasterxml.jackson.annotation.JsonIgnore
import io.opentelemetry.sdk.common.InstrumentationScopeInfo
import io.opentelemetry.sdk.metrics.data.DoublePointData
import io.opentelemetry.sdk.metrics.data.ExponentialHistogramData
import io.opentelemetry.sdk.metrics.data.GaugeData
import io.opentelemetry.sdk.metrics.data.HistogramData
import io.opentelemetry.sdk.metrics.data.LongPointData
import io.opentelemetry.sdk.metrics.data.SumData
import io.opentelemetry.sdk.metrics.data.SummaryData
import io.opentelemetry.sdk.resources.Resource

/**
 * Jackson mixin for ignoring some fields during serialization.
 * Counterpart of io.opentelemetry.sdk.metrics.data.MetricData
 */
internal abstract class MetricDataMixIn {
  @JsonIgnore
  abstract fun getResource(): Resource?

  @JsonIgnore
  abstract fun getInstrumentationScopeInfo(): InstrumentationScopeInfo?

  @JsonIgnore
  abstract fun isEmpty(): Boolean

  @JsonIgnore
  abstract fun getDoubleGaugeData(): GaugeData<DoublePointData>

  @JsonIgnore
  abstract fun getLongGaugeData(): GaugeData<LongPointData>

  @JsonIgnore
  abstract fun getDoubleSumData(): SumData<DoublePointData>

  @JsonIgnore
  abstract fun getLongSumData(): SumData<LongPointData>

  @JsonIgnore
  abstract fun getSummaryData(): SummaryData

  @JsonIgnore
  abstract fun getHistogramData(): HistogramData

  @JsonIgnore
  abstract fun getExponentialHistogramData(): ExponentialHistogramData
}