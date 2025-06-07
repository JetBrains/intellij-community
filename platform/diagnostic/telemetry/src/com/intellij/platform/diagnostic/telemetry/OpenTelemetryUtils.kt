// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileSetLimiter
import io.opentelemetry.sdk.metrics.data.DoublePointData
import io.opentelemetry.sdk.metrics.data.LongPointData
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.data.MetricDataType
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
object OpenTelemetryUtils {
  /**
   * Report span telemetry to the client instead of OTLP. Requires OTLP to be enabled.
   */
  const val RDCT_TRACING_DIAGNOSTIC_FLAG: String = "rdct.diagnostic.otlp"
  const val RDCT_CONN_METRICS_DIAGNOSTIC_FLAG: String = "rdct.connection.metrics.enabled"
  const val RDCT_LUX_METRICS_DIAGNOSTIC_FLAG: String = "lux.metrics.enabled"

  fun toCsvStream(metricData: MetricData): Sequence<String> {
    return when (metricData.type) {
      MetricDataType.LONG_SUM -> metricData.longSumData.points.asSequence().map { p: LongPointData ->
        concatToCsvLine(metricData.name, p.startEpochNanos.toString(), p.epochNanos.toString(), p.value.toString())
      }
      MetricDataType.DOUBLE_SUM -> metricData.doubleSumData.points.asSequence().map { p: DoublePointData ->
        concatToCsvLine(metricData.name, p.startEpochNanos.toString(), p.epochNanos.toString(), p.value.toString())
      }
      MetricDataType.LONG_GAUGE -> metricData.longGaugeData.points.asSequence().map { p: LongPointData ->
        concatToCsvLine(metricData.name, p.startEpochNanos.toString(), p.epochNanos.toString(), p.value.toString())
      }
      MetricDataType.DOUBLE_GAUGE -> metricData.doubleGaugeData.points.asSequence().map { p: DoublePointData ->
        concatToCsvLine(metricData.name, p.startEpochNanos.toString(), p.epochNanos.toString(), p.value.toString())
      }
      else -> sequenceOf(concatToCsvLine(metricData.name, "-1", "-1", "<metrics type " + metricData.type + " is not supported yet>"))
    }
  }

  private fun concatToCsvLine(name: String, vararg values: String): String {
    return ("$name,${values.joinToString(separator = ",")}")
  }

  fun csvHeadersLines(): List<String> {
    return listOf(
      "# OpenTelemetry Metrics report: .csv, 4 fields: ",
      "# <metric name>, <period start, nanoseconds>, <period end, nanoseconds>, <metric value>",
      "# See CsvMetricsExporter for details.",
      "# ",
      "# NAME, PERIOD_START_NANOS, PERIOD_END_NANOS, VALUE")
  }

  /** @return base path for metrics reporting, or null, if metrics reporting is configured to be off */
  private fun resolveMetricsReportingPath(rawPath: String): Path? {
    if (rawPath.isBlank()) {
      return null
    }
    // if a metrics path is relative -> resolve it against IDEA logDir:
    return PathManager.getLogDir().resolve(rawPath).toAbsolutePath()
  }

  fun metricsCsvReportingPath(): Path? =
    resolveMetricsReportingPath(System.getProperty("idea.diagnostic.opentelemetry.metrics.file", "open-telemetry-metrics.csv"))

  fun metricsJsonReportingPath(): Path? =
    resolveMetricsReportingPath(System.getProperty("idea.diagnostic.opentelemetry.meters.file.json", "open-telemetry-meters.json"))

  fun setupFileLimiterForMetrics(metricsReportingBasePath: Path): FileSetLimiter {
    val suffixDateFormat = System.getProperty("idea.diagnostic.opentelemetry.metrics.suffix-date-format", "yyyy-MM-dd-HH-mm-ss")
    return FileSetLimiter
      .inDirectory(metricsReportingBasePath.parent)
      .withBaseNameAndDateFormatSuffix(metricsReportingBasePath.fileName.toString(), suffixDateFormat)
  }
}