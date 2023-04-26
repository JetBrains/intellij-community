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
object MetricsExporterUtils {

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
      "# OpenTelemetry Metrics report: .csv, 4 fields: ",
      "# <metric name>, <period start, nanoseconds>, <period end, nanoseconds>, <metric value>",
      "# See CsvMetricsExporter for details.",
      "# ",
      "# NAME, PERIOD_START_NANOS, PERIOD_END_NANOS, VALUE")
  }

  /** @return base path for metrics reporting, or null, if metrics reporting is configured to be off */
  @JvmStatic
  fun metricsReportingPath(): Path? {
    val metricsReportingPath = System.getProperty("idea.diagnostic.opentelemetry.metrics.file", "open-telemetry-metrics.csv")
    if(metricsReportingPath.isBlank()){
      return null
    }
    //if metrics path is relative -> resolve it against IDEA logDir:
    return PathManager.getLogDir().resolve(metricsReportingPath).toAbsolutePath()
  }

  @JvmStatic
  fun setupFileLimiterForMetrics(metricsReportingBasePath: Path): FileSetLimiter {
    val suffixDateFormat = System.getProperty("idea.diagnostic.opentelemetry.metrics.suffix-date-format", "yyyy-MM-dd-HH-mm-ss")
    return FileSetLimiter
      .inDirectory(metricsReportingBasePath.parent)
      .withBaseNameAndDateFormatSuffix(metricsReportingBasePath.fileName.toString(), suffixDateFormat)
  }

  /**
   * Creates new file for reporting OTel metrics.
   *
   * Generated file name is derived from metricsReportingBasePath, but with date/time/sequential-number additions,
   * i.e. '/var/logs/open-telemetry-metrics.csv' (base) -> '/var/logs/open-telemetry-metrics-2023-02-04-14-15-43.1.csv'
   * (exact format is configurable).
   *
   * Method uses Date/time/seqNo additions to always create new file, not existent in the metricsReportingBasePath.parent
   * folder before the call.
   *
   * Method also keeps the total number of generated files limited: it removes the oldest files if there are
   * too many files already generated.
   *
   * If metricsReportingBasePath is relative -> method resolves it against IDEA logs folder to make absolute.
   */
  @JvmStatic
  fun generateFileForMetrics(metricsReportingBasePath: Path): Path {
    val maxFilesToKeep = SystemProperties.getIntProperty("idea.diagnostic.opentelemetry.metrics.max-files-to-keep", 14)

    val fileLimiterForMetrics = setupFileLimiterForMetrics(metricsReportingBasePath)
    return fileLimiterForMetrics.removeOldFilesBut(maxFilesToKeep, FileSetLimiter.DELETE_ASYNC)
      .createNewFile()
  }
}