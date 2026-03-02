// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.exporters.meters

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.diagnostic.telemetry.exporters.RollingFileSupplier
import com.intellij.platform.diagnostic.telemetry.exporters.meters.models.MetricDataMixIn
import com.intellij.platform.diagnostic.telemetry.exporters.meters.models.PointDataMixIn
import io.opentelemetry.sdk.common.CompletableResultCode
import io.opentelemetry.sdk.metrics.InstrumentType
import io.opentelemetry.sdk.metrics.data.AggregationTemporality
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.data.PointData
import io.opentelemetry.sdk.metrics.export.MetricExporter
import org.jetbrains.annotations.ApiStatus
import tools.jackson.module.kotlin.addMixIn
import tools.jackson.module.kotlin.jacksonMapperBuilder
import java.io.IOException
import java.nio.file.StandardOpenOption
import kotlin.io.path.outputStream

private val LOG: Logger
  get() = logger<TelemetryMeterJsonExporter>()

/**
 * Export [MetricData] into a JSON file.
 */
@ApiStatus.Internal
class TelemetryMeterJsonExporter(private val writeToFileSupplier: RollingFileSupplier) : MetricExporter {

  init {
    writeToFileSupplier.init()
  }

  override fun getAggregationTemporality(instrumentType: InstrumentType): AggregationTemporality {
    return AggregationTemporality.DELTA
  }

  override fun export(metrics: Collection<MetricData>): CompletableResultCode {
    if (metrics.isEmpty()) {
      return CompletableResultCode.ofSuccess()
    }

    val result = CompletableResultCode()
    val writeToFile = writeToFileSupplier.get(forceToGetNewPath = true)

    try {
      jacksonMapperBuilder()
        .addMixIn<MetricData, MetricDataMixIn>()
        .addMixIn<PointData, PointDataMixIn>()
        .build()
        .writeValue(writeToFile.outputStream(StandardOpenOption.CREATE, StandardOpenOption.APPEND), metrics)

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
