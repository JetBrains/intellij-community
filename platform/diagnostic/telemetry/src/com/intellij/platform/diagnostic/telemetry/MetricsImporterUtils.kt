// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry

import com.intellij.openapi.diagnostic.logger
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
  private fun fromCsvFile(metricsCsvPath: Path): HashMap<String, MutableList<LongPointData>> {
    val meters = HashMap<String, MutableList<LongPointData>>()

    metricsCsvPath.bufferedReader().useLines { lines ->
      for (line in lines.drop(OpenTelemetryUtils.csvHeadersLines().size)) {
        if (line.trim().isEmpty()) continue

        try {
          // See: [MetricsExporterUtils.concatToCsvLine]
          // # NAME, PERIOD_START_NANOS, PERIOD_END_NANOS, VALUE
          val (metricName, startEpochNanos, endEpochNanos, value) = line.split(",")

          if (value.isLongNumber()) {
            val data: LongPointData = ImmutableLongPointData.create(startEpochNanos.toLong(),
                                                                    endEpochNanos.toLong(),
                                                                    Attributes.empty(),
                                                                    value.toLong())
            val metrics = meters.computeIfAbsent(metricName) { mutableListOf() }
            metrics.add(data)
          }
        }
        catch (e: Exception) {
          logger<MetricsImporterUtils>().error("Failure during parsing OpenTelemetry metrics from CSV file $metricsCsvPath on line $line")
          throw e
        }
      }

      return meters
    }
  }

  fun fromCsvFile(metricsCsvFiles: Iterable<Path>): HashMap<String, MutableList<LongPointData>> {
    val meters = HashMap<String, MutableList<LongPointData>>()

    for (csvFile in metricsCsvFiles) {
      val currentMeters = fromCsvFile(csvFile)
      currentMeters.forEach { (key, value) ->
        meters.merge(key, value) { originalList, additionalList -> originalList.plus(additionalList).toMutableList() }
      }
    }

    return meters
  }
}