// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.telemetry

import io.opentelemetry.sdk.metrics.data.DoublePointData
import io.opentelemetry.sdk.metrics.data.LongPointData
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.data.MetricDataType
import org.jetbrains.annotations.ApiStatus
import java.util.stream.Stream

@ApiStatus.Internal
open class BasicCsvMetricsExporter {
  companion object {
    @JvmStatic
    fun toCsvStream(metricData: MetricData): Stream<String> {
      return when (metricData.type) {
        MetricDataType.LONG_SUM -> metricData.longSumData.points.stream().map { p: LongPointData ->
          concatToCsvLine(metricData.name, p.startEpochNanos, p.epochNanos, p.value.toString())
        }
        MetricDataType.DOUBLE_SUM -> metricData.doubleSumData.points.stream().map { p: DoublePointData ->
          concatToCsvLine(metricData.name, p.startEpochNanos, p.epochNanos, p.value.toString())
        }
        MetricDataType.LONG_GAUGE -> metricData.longGaugeData.points.stream().map { p: LongPointData ->
          concatToCsvLine(metricData.name, p.startEpochNanos, p.epochNanos, p.value.toString())
        }
        MetricDataType.DOUBLE_GAUGE -> metricData.doubleGaugeData.points.stream().map { p: DoublePointData ->
          concatToCsvLine(metricData.name, p.startEpochNanos, p.epochNanos, p.value.toString())
        }
        else -> Stream.of(concatToCsvLine(metricData.name, -1, -1, "<metrics type " + metricData.type + " is not supported yet>"))
      }
    }

    private fun concatToCsvLine(name: String, startEpochNanos: Long, endEpochNanos: Long, value: String): String {
      return ("$name,$startEpochNanos,$endEpochNanos,$value")
    }

    fun csvHeadersLines(): List<String> {
      return listOf(
        "# OpenTelemetry Metrics report: .gz, 4 fields: ",
        "# <metric name>, <period start, nanoseconds>, <period end, nanoseconds>, <metric value>",
        "# See BasicCsvMetricsExporter for details.",
        "# ",
        "# NAME, PERIOD_START_NANOS, PERIOD_END_NANOS, VALUE")
    }
  }
}