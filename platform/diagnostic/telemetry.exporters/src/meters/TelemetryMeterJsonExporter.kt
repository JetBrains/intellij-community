// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.exporters.meters

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.diagnostic.telemetry.OpenTelemetryUtils
import com.intellij.platform.diagnostic.telemetry.exporters.RollingFileSupplier
import com.intellij.platform.diagnostic.telemetry.exporters.initRollingFileSupplier
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.metrics.InstrumentType
import io.opentelemetry.sdk.metrics.data.*
import io.opentelemetry.sdk.metrics.export.MetricExporter
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import kotlin.time.Duration.Companion.nanoseconds

private val LOG: Logger
  get() = logger<TelemetryMeterJsonExporter>()

/**
 * Export [MetricData] into a JSON file.
 */
@ApiStatus.Internal
class TelemetryMeterJsonExporter(private val writeToFileSupplier: RollingFileSupplier) : MetricExporter {

  init {
    initRollingFileSupplier(writeToFileSupplier)
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

    //MetricDataType.HISTOGRAM -> metricData.histogramData.points.asSequence().map { p: HistogramPointData ->
    //  OpenTelemetryUtils.concatToCsvLine(
    //    metricData.name,
    //    p.startEpochNanos.toString(),
    //    p.epochNanos.toString(),
    //    p.count.toString(),
    //    p.sum.toString(),
    //    p.counts.mapIndexed { index, countInBucket ->
    //      val upperBoundary = if (index == p.counts.lastIndex) Double.POSITIVE_INFINITY
    //      else p.boundaries[index]
    //
    //      "[${upperBoundary.nanoseconds} count: $countInBucket]"
    //    }.joinToString()
    //  )
    //}

    val lines: List<String> = metrics.asSequence()
      .flatMap {
        listOf("")
        // TODO("write json chunks into the aggregated JSON file")
      }
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
