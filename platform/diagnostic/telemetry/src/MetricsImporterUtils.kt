// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.telemetry

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.metrics.data.LongPointData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableLongPointData
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.bufferedReader

@ApiStatus.Internal
object MetricsImporterUtils {

  private fun String.isLongNumber(): Boolean {
    return try {
      this.toLong()
      true
    }
    catch (e: NumberFormatException) {
      false
    }
  }


  /**
   * Only Long Gauge meters are parsed by now.
   * TODO: to differentiate between different types of meters we need to store MetricDataType in csv as well
   */
  @JvmStatic
  fun fromCsvFile(metricsCsvPath: Path): HashMap<String, MutableList<LongPointData>> {
    val meters = HashMap<String, MutableList<LongPointData>>()

    metricsCsvPath.bufferedReader().useLines { lines ->
      lines.drop(MetricsExporterUtils.csvHeadersLines().size).forEach {
        // See: [MetricsExporterUtils.concatToCsvLine]
        // # NAME, PERIOD_START_NANOS, PERIOD_END_NANOS, VALUE
        val (metricName, startEpochNanos, endEpochNanos, value) = it.split(",")

        if (value.isLongNumber()) {
          val data: LongPointData = ImmutableLongPointData.create(startEpochNanos.toLong(),
                                                                  endEpochNanos.toLong(),
                                                                  Attributes.empty(),
                                                                  value.toLong())
          val metrics = meters.computeIfAbsent(metricName) { mutableListOf() }
          metrics.add(data)
        }
      }

      return meters
    }
  }
}