// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.telemetry

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.util.io.FileSetLimiter
import com.intellij.util.SystemProperties
import io.opentelemetry.sdk.metrics.data.DoublePointData
import io.opentelemetry.sdk.metrics.data.LongPointData
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.data.MetricDataType
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import java.util.stream.Stream

@ApiStatus.Internal
open class MetricsExporterUtils {
  companion object {
    val connectionMetricsPath: String = System.getProperty("idea.diagnostic.opentelemetry.metrics.file",
                                                           "open-telemetry-connection-metrics.gz")
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

    @JvmStatic
    fun csvHeadersLines(): List<String> {
      return listOf(
        "# OpenTelemetry Metrics report: .gz, 4 fields: ",
        "# <metric name>, <period start, nanoseconds>, <period end, nanoseconds>, <metric value>",
        "# See BasicCsvMetricsExporter for details.",
        "# ",
        "# NAME, PERIOD_START_NANOS, PERIOD_END_NANOS, VALUE")
    }

    @JvmStatic
    fun generateFileForMetrics(metricsReportingPath: String): Path {
      val suffixDateFormat = System.getProperty("idea.diagnostic.opentelemetry.metrics.suffix-date-format", "yyyy-MM-dd-HH-mm-ss")
      val maxFilesToKeep = SystemProperties.getIntProperty("idea.diagnostic.opentelemetry.metrics.max-files-to-keep", 14)

      //if metrics path is relative -> resolve it against IDEA logDir:
      val pathResolvedAgainstLogDir = PathManager.getLogDir().resolve(metricsReportingPath).toAbsolutePath()

      return FileSetLimiter.inDirectory(pathResolvedAgainstLogDir.parent).withBaseNameAndDateFormatSuffix(
        pathResolvedAgainstLogDir.fileName.toString(), suffixDateFormat).removeOldFilesBut(maxFilesToKeep,
                                                                                           FileSetLimiter.DELETE_ASYNC).createNewFile()
    }
  }
}