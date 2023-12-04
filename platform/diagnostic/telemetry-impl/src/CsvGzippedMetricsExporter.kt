// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.impl

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.util.io.FileSetLimiter
import com.intellij.platform.diagnostic.telemetry.OpenTelemetryUtils.toCsvStream
import com.intellij.util.SystemProperties
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.metrics.InstrumentType
import io.opentelemetry.sdk.metrics.data.AggregationTemporality
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.export.MetricExporter
import java.nio.file.Files
import java.nio.file.Path

class CsvGzippedMetricsExporter(private var fileToWrite: Path) : MetricExporter {
  private var writeToFile: Path = fileToWrite
  private val storage = LinesStorage(fileToWrite)

  init {
    initFileCreating(writeToFile)
  }

  private fun initFileCreating(writeToFile: Path) {
    if (!Files.exists(writeToFile)) {
      val parentDir = writeToFile.parent
      if (!Files.isDirectory(parentDir)) {
        Files.createDirectories(parentDir)
      }
    }
    runCatching {
      val tmp = ArrayList<String>()
      val lines = storage.getLines()
      if (lines.isNotEmpty()) {
        tmp.addAll(lines)
        storage.clearStorage()
      }
      storage.appendHeaderLines()
      if (tmp.size > 0) {
        for (line in tmp) {
          storage.appendLine(line)
        }
      }
    }.getOrLogException(LOG)
  }

  override fun getAggregationTemporality(instrumentType: InstrumentType): AggregationTemporality {
    return AggregationTemporality.DELTA
  }

  override fun export(metrics: Collection<MetricData>): CompletableResultCode {
    if (metrics.isEmpty()) {
      return CompletableResultCode.ofSuccess()
    }

    val result = CompletableResultCode()

    metrics.forEach { toCsvStream(it).forEach(storage::appendLine) }

    try {
      val fileSize = Files.size(fileToWrite)
      if (fileSize > 10 * 1024 * 1024) {
        updateFileForMetrics()
      }
      storage.dump()
      result.succeed()
    }
    catch (e: Exception) {
      LOG.error("Error occurred when writing metrics to file", e)
      result.fail()
    }
    return result
  }

  private fun updateFileForMetrics() {
    val newPath = generatePathForConnectionMetrics()
    fileToWrite = newPath
    initFileCreating(newPath)
    storage.updateDestFile(fileToWrite)
  }

  override fun flush(): CompletableResultCode? {
    updateFileForMetrics()
    return CompletableResultCode.ofSuccess()
  }

  override fun shutdown(): CompletableResultCode? {
    storage.closeBufferedWriter()
    return CompletableResultCode.ofSuccess()
  }

  companion object {
    private val LOG: Logger = Logger.getInstance(CsvGzippedMetricsExporter::class.java)

    fun generatePathForConnectionMetrics(): Path {
      val connectionMetricsPath = "open-telemetry-connection-metrics.csv.gz"
      val pathResolvedAgainstLogDir = PathManager.getLogDir().resolve(connectionMetricsPath).toAbsolutePath()
      val maxFilesToKeep = SystemProperties.getIntProperty("idea.diagnostic.opentelemetry.rdct.metrics.max-files-to-keep", 14)

      return generatePath(pathResolvedAgainstLogDir, maxFilesToKeep)
    }

    fun generatePathForLuxMetrics(): Path {
      val luxMetricsPath = "open-telemetry-lux-metrics.csv.gz"
      val pathResolvedAgainstLogDir = PathManager.getLogDir().resolve(luxMetricsPath).toAbsolutePath()
      val maxFilesToKeep = SystemProperties.getIntProperty("idea.diagnostic.opentelemetry.lux.metrics.max-files-to-keep", 14)

      return generatePath(pathResolvedAgainstLogDir, maxFilesToKeep)
    }

    private fun generatePath(pathResolvedAgainstLogDir: Path, maxFilesToKeep: Int): Path {
      return FileSetLimiter.inDirectory(pathResolvedAgainstLogDir.parent).withBaseNameAndDateFormatSuffix(
        pathResolvedAgainstLogDir.fileName.toString(), "yyyy-MM-dd-HH-mm-ss").removeOldFilesBut(maxFilesToKeep,
                                                                                                FileSetLimiter.DELETE_ASYNC).createNewFile()
    }
  }
}