// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.exporters.meters

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.common.InstrumentationScopeInfo
import io.opentelemetry.sdk.metrics.data.*
import io.opentelemetry.sdk.metrics.internal.data.*
import io.opentelemetry.sdk.resources.Resource
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path
import kotlin.io.path.fileSize

/**
 * Imports metrics exported by com.intellij.platform.diagnostic.telemetry.exporters.meters.TelemetryMeterJsonExporter
 */
@ApiStatus.Internal
object OpenTelemetryMetersJsonImporter {
  fun fromJsonFile(jsonPath: Path): Collection<MetricData> {
    val module = SimpleModule().apply { addDeserializer(MetricData::class.java, MetricDataDeserializer()) }

    if (jsonPath.fileSize() == 0L) return emptyList()

    return jacksonObjectMapper()
      .registerModule(module)
      .readValue<Collection<MetricData>>(jsonPath.toFile())
  }
}

internal class MetricDataDeserializer : JsonDeserializer<MetricData>() {
  companion object {
    private val emptyResource = Resource.empty()
    private val emptyInstrumentationScope = InstrumentationScopeInfo.empty()

    private fun getIsMonotonic(dataNode: JsonNode): Boolean = dataNode.get("monotonic").asBoolean(false)

    private fun getAggregationTemporality(dataNode: JsonNode): AggregationTemporality =
      AggregationTemporality.valueOf(dataNode.get("aggregationTemporality").asText())

    private fun getStartEpoch(dataPointNode: JsonNode): Long = dataPointNode.get("startEpochNanos").asLong()

    private fun getEpoch(dataPointNode: JsonNode): Long = dataPointNode.get("epochNanos").asLong()

    private fun getValue(dataPointNode: JsonNode): JsonNode = dataPointNode.get("value")

    private fun getAttributes(dataPointNode: JsonNode): Attributes = Attributes.builder().apply {
      dataPointNode.get("attributes").properties().forEach { this.put(it.key.toString(), it.value.toString()) }
    }.build()

    private fun getLongPointsData(pointsNode: JsonNode): List<LongPointData> = pointsNode.map { dataPoint ->
      val startEpoch = getStartEpoch(dataPoint)
      val epoch = getEpoch(dataPoint)
      val value = getValue(dataPoint)
      val attributes = getAttributes(dataPoint)

      ImmutableLongPointData.create(startEpoch, epoch, attributes, value.asLong())
    }

    private fun getDoublePointsData(pointsNode: JsonNode): List<DoublePointData> = pointsNode.map { dataPoint ->
      val startEpoch = getStartEpoch(dataPoint)
      val epoch = getEpoch(dataPoint)
      val value = getValue(dataPoint)
      val attributes = getAttributes(dataPoint)

      ImmutableDoublePointData.create(startEpoch, epoch, attributes, value.asDouble())
    }
  }

  override fun deserialize(jsonParser: JsonParser, ctxt: DeserializationContext): MetricData {
    val node: JsonNode = jsonParser.codec.readTree(jsonParser)

    val name = node.get("name").asText()
    val type = node.get("type").asText()
    val unit = node.get("unit").asText()
    val description = node.get("description").asText()

    val data = node.get("data")
    val points = data.get("points")

    return when (MetricDataType.valueOf(type)) {
      // Long counter
      MetricDataType.LONG_SUM -> {
        val longPoints = getLongPointsData(points)
        val sumData = ImmutableSumData.create(getIsMonotonic(data), getAggregationTemporality(data), longPoints)

        ImmutableMetricData.createLongSum(emptyResource, emptyInstrumentationScope, name, description, unit, sumData)
      }
      // Double counter
      MetricDataType.DOUBLE_SUM -> {
        val doublePoints = getDoublePointsData(points)
        val sumData = ImmutableSumData.create(getIsMonotonic(data), getAggregationTemporality(data), doublePoints)

        ImmutableMetricData.createDoubleSum(emptyResource, emptyInstrumentationScope, name, description, unit, sumData)
      }
      MetricDataType.LONG_GAUGE -> {
        val longPoints = getLongPointsData(points)
        val gaugeData = ImmutableGaugeData.create(longPoints)

        ImmutableMetricData.createLongGauge(emptyResource, emptyInstrumentationScope, name, description, unit, gaugeData)
      }
      MetricDataType.DOUBLE_GAUGE -> {
        val doublePoints = getDoublePointsData(points)
        val gaugeData = ImmutableGaugeData.create(doublePoints)

        ImmutableMetricData.createDoubleGauge(emptyResource, emptyInstrumentationScope, name, description, unit, gaugeData)
      }
      MetricDataType.SUMMARY -> TODO("Summary deserialization isn't supported yet")
      MetricDataType.HISTOGRAM -> {
        val pointsData = points.map { dataPoint ->
          val startEpoch = getStartEpoch(dataPoint)
          val epoch = getEpoch(dataPoint)
          val attributes = getAttributes(dataPoint)

          val max = dataPoint.get("max").asDouble(Double.POSITIVE_INFINITY)
          val sum = dataPoint.get("sum").asDouble()
          val min = dataPoint.get("min").asDouble(Double.NEGATIVE_INFINITY)
          val counts = dataPoint.get("counts").map { it.asLong() }
          val boundaries = dataPoint.get("boundaries").map { it.asDouble() }

          ImmutableHistogramPointData.create(startEpoch, epoch, attributes, sum,
                                             min != Double.NEGATIVE_INFINITY, min,
                                             max != Double.POSITIVE_INFINITY, max,
                                             boundaries, counts)
        }

        val histogramData = ImmutableHistogramData.create(getAggregationTemporality(data), pointsData)

        ImmutableMetricData.createDoubleHistogram(emptyResource, emptyInstrumentationScope, name, description, unit, histogramData)
      }
      MetricDataType.EXPONENTIAL_HISTOGRAM -> TODO("Exponential histogram isn't supported yet")
    }
  }
}
