// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.opentelemetry

import com.intellij.diagnostic.FreezeProfiler
import com.intellij.platform.diagnostic.telemetry.OpenTelemetryUtils
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.logger
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * This FreezeProfiler impl adds the most recent OTel.Metrics file to the freeze reports.
 *
 * OTel.Metrics reporting is enabled by default, so start/stop methods do nothing, while {@link #getAttachments()} method
 * finds and attaches the most recent OTel.Metrics report.
 *
 * Profiler limits attachment size to MAX_METRICS_LINES_TO_ATTACH.
 */
class OTelMetricsFreezeProfiler : FreezeProfiler {
  override fun start(reportDir: Path) {
  }

  override fun stop() {
  }

  override fun getAttachments(reportDir: Path): List<Attachment> = collectOpenTelemetryReports()
}

/**
 * The whole metrics file could be quite large, so limit attachment to <=5000 lines
 * (which is ~500Kb, ~1.5h min of recording):
 */
private val MAX_METRICS_LINES_TO_ATTACH = System.getProperty("idea.freeze.otel.max-metrics-lines-to-attach", "").toIntOrNull() ?: 5000

/**
 * @return OTel metrics reports (.csv) as an [Attachment].
 * Currently, returns only a single, the most recent report, or an empty list of no recent reports could be found,
 * or an error happens during the report content loading.
 */
private fun collectOpenTelemetryReports(): List<Attachment> {
  val logDir = Path.of(PathManager.getLogPath())
  if (Files.isDirectory(logDir)) {
    try {
      val mostRecentFile = listMetricsFiles().maxByOrNull { it.fileName }
      mostRecentFile ?: return emptyList()

      val lines = Files.readAllLines(mostRecentFile)
      //The whole metrics file could be quite large, so limit attachment size:
      val tailLines = lines.takeLast(MAX_METRICS_LINES_TO_ATTACH)
        .joinToString("\n")
      return listOf(
        Attachment(
          mostRecentFile.fileName.toString(),
          tailLines
        )
      )
    }
    catch (ex: IOException) {
      logger<OTelMetricsFreezeProfiler>().info("Error reading most recent open-telemetry-metrics.csv file", ex)
    }
  }
  return emptyList()
}

private fun listMetricsFiles(): List<Path> {
  val metricsReportingPath = OpenTelemetryUtils.metricsReportingPath()
  metricsReportingPath ?: return emptyList()

  val fileLimiterForMetrics = OpenTelemetryUtils.setupFileLimiterForMetrics(metricsReportingPath)
  return fileLimiterForMetrics.listExistentFiles()
}