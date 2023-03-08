// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.telemetry

import com.intellij.diagnostic.telemetry.MetricsExporterUtils.Companion.toCsvStream
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.runAndLogException
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.metrics.InstrumentType
import io.opentelemetry.sdk.metrics.data.AggregationTemporality
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.export.MetricExporter
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class CsvGzippedMetricsExporter(writeToFile: Path) : MetricExporter {
  private var fileToWrite: File = writeToFile.toFile()
  private var storage = LinesStorage(fileToWrite)

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
    logger.runAndLogException {
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

    metrics.stream().flatMap { metricData: MetricData ->
      toCsvStream(metricData)
    }.map { line -> storage.appendLine(line) }.toList()

    try {
      val fileSize = fileToWrite.length()
      if (fileSize > 10 * 1024 * 1024) {
        val newPath = MetricsExporterUtils.generateFileForMetrics(MetricsExporterUtils.connectionMetricsPath)
        fileToWrite = newPath.toFile()
        initFileCreating(newPath)
        storage.updateDestFile(fileToWrite)
      }
      storage.dump()
      result.succeed()
    }
    catch (e: Exception) {
      logger.error("Error occurred when writing metrics to file", e)
      result.fail()
    }
    return result
  }
  override fun flush(): CompletableResultCode? {
    return CompletableResultCode.ofSuccess()
  }
  override fun shutdown(): CompletableResultCode? {
    storage.closeBufferedWriter()
    return CompletableResultCode.ofSuccess()
  }
  companion object {
    val logger = Logger.getInstance(CsvGzippedMetricsExporter::class.java)
  }
}