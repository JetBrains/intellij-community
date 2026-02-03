// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.exporters.meters

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.diagnostic.telemetry.OpenTelemetryUtils
import com.intellij.platform.diagnostic.telemetry.exporters.RollingFileSupplier
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.metrics.InstrumentType
import io.opentelemetry.sdk.metrics.data.AggregationTemporality
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.export.MetricExporter
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

private val LOG: Logger
  get() = logger<CsvMetricsExporter>()

/** Copy html file with plotting scripts into targetDir  */
private fun copyHtmlPlotterToOutputDir(targetDir: Path) {
  val targetToCopyTo = targetDir.resolve(CsvMetricsExporter.HTML_PLOTTER_NAME)
  val plotterHtmlUrl = CsvMetricsExporter::class.java.classLoader.getResource(CsvMetricsExporter.HTML_PLOTTER_NAME)
  if (plotterHtmlUrl == null) {
    LOG.warn("${CsvMetricsExporter.HTML_PLOTTER_NAME} is not found in classpath")
  }
  else {
    plotterHtmlUrl.openStream().use { stream ->
      val bytes = stream.readAllBytes()
      Files.write(targetToCopyTo, bytes)
    }
  }
}

/**
 * Export [MetricData] into a file in a simple CSV format:
 * name, epochStartNanos, epochEndNanos, value
 * <br></br>
 * <br></br>
 * Only simplest metrics types are supported (e.g. no support for histograms),
 * no support for attributes, and IO/file format itself is not the most effective one.
 * <br></br>
 *
 * TODO not all metrics types are supported now, see .toCSVLine()
 * TODO no support for attributes now, see .toCSVLine()
 *
 * @see com.intellij.platform.diagnostic.telemetry.exporters.meters.TelemetryMeterJsonExporter
 */
@ApiStatus.Internal
class CsvMetricsExporter(private val writeToFileSupplier: RollingFileSupplier) : MetricExporter {
  companion object {
    @VisibleForTesting
    const val HTML_PLOTTER_NAME: String = "open-telemetry-metrics-plotter.html"
  }

  init {
    writeToFileSupplier.init(OpenTelemetryUtils.csvHeadersLines()).also {
      copyHtmlPlotterToOutputDir(it.parent)
    }
  }

  override fun getAggregationTemporality(instrumentType: InstrumentType): AggregationTemporality {
    return AggregationTemporality.DELTA
  }

  override fun export(metrics: Collection<MetricData>): CompletableResultCode {
    if (metrics.isEmpty()) {
      return CompletableResultCode.ofSuccess()
    }

    val result = CompletableResultCode()
    val writeToFile = writeToFileSupplier.get()

    val lines = metrics.asSequence()
      .flatMap { OpenTelemetryUtils.toCsvStream(it) }
      .toList()
    try {
      Files.write(writeToFile, lines, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
      result.succeed()
    }
    catch (e: IOException) {
      LOG.warn("Can't write metrics into " + writeToFile.toAbsolutePath(), e)
      result.fail()
    }
    return result
  }

  override fun flush(): CompletableResultCode = CompletableResultCode.ofSuccess()

  override fun shutdown(): CompletableResultCode = CompletableResultCode.ofSuccess()
}