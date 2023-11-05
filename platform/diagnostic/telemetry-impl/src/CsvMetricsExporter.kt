// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.diagnostic.telemetry.OpenTelemetryUtils.csvHeadersLines
import com.intellij.platform.diagnostic.telemetry.OpenTelemetryUtils.toCsvStream
import com.intellij.platform.diagnostic.telemetry.impl.CsvMetricsExporter.Companion.HTML_PLOTTER_NAME
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

/**
 * Export [MetricData] into a file in a simple CSV format:
 * name, epochStartNanos, epochEndNanos, value
 * <br></br>
 * <br></br>
 * This is expected to be temporary solution for metrics export -- until full-fledged (json?) exporter will be implemented.
 * That is why implementation is quite limited: only simplest metrics types are supported (e.g. no support for histograms),
 * no support for attributes, and IO/file format itself is not the most effective one. But until now it seems like this limited
 * implementation could be enough at least for a while.
 *
 * <br></br>
 *
 * TODO not all metrics types are supported now, see .toCSVLine()
 * TODO no support for attributes now, see .toCSVLine()
 */
@ApiStatus.Internal
class CsvMetricsExporter internal constructor(private val writeToFileSupplier: RollingFileSupplier) : MetricExporter {
  companion object {
    @VisibleForTesting
    const val HTML_PLOTTER_NAME: String = "open-telemetry-metrics-plotter.html"
  }

  init {
    val writeToFile = writeToFileSupplier.get()
    if (!Files.exists(writeToFile)) {
      val parentDir = writeToFile.parent
      if (!Files.isDirectory(parentDir)) {
        //RC: createDirectories() _does_ throw FileAlreadyExistsException if path is a _symlink_ to a directory, not a directory
        // itself (JDK-8130464). Check !isDirectory() above should work around that case.
        Files.createDirectories(parentDir)
      }
    }
    if (!Files.exists(writeToFile) || Files.size(writeToFile) == 0L) {
      Files.write(writeToFile, csvHeadersLines(), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
    }
    copyHtmlPlotterToOutputDir(writeToFile.parent)
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
      .flatMap { toCsvStream(it) }
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

/** Copy html file with plotting scripts into targetDir  */
private fun copyHtmlPlotterToOutputDir(targetDir: Path) {
  val targetToCopyTo = targetDir.resolve(HTML_PLOTTER_NAME)
  val plotterHtmlUrl = CsvMetricsExporter::class.java.getResource(HTML_PLOTTER_NAME)
  if (plotterHtmlUrl == null) {
    LOG.warn("$HTML_PLOTTER_NAME is not found in classpath")
  }
  else {
    plotterHtmlUrl.openStream().use { stream ->
      val bytes = stream.readAllBytes()
      Files.write(targetToCopyTo, bytes)
    }
  }
}
