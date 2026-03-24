// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry.exporters.meters

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.sdk.common.InstrumentationScopeInfo
import io.opentelemetry.sdk.metrics.data.AggregationTemporality
import io.opentelemetry.sdk.metrics.data.DoublePointData
import io.opentelemetry.sdk.metrics.data.LongPointData
import io.opentelemetry.sdk.metrics.data.MetricData
import io.opentelemetry.sdk.metrics.data.MetricDataType
import io.opentelemetry.sdk.metrics.internal.data.ImmutableDoublePointData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableGaugeData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableHistogramData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableHistogramPointData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableLongPointData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableMetricData
import io.opentelemetry.sdk.metrics.internal.data.ImmutableSumData
import io.opentelemetry.sdk.resources.Resource
import org.jetbrains.annotations.ApiStatus
import tools.jackson.core.JsonParser
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ValueDeserializer
import tools.jackson.databind.module.SimpleModule
import tools.jackson.module.kotlin.jacksonMapperBuilder
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

    return jacksonMapperBuilder()
      .addModule(module)
      .build()
      .readValue(jsonPath, object : TypeReference<Collection<MetricData>>() {})
  }
}

internal class MetricDataDeserializer : ValueDeserializer<MetricData>() {
  companion object {
    private val emptyResource = Resource.empty()
    private val emptyInstrumentationScope = InstrumentationScopeInfo.empty()

    private fun getIsMonotonic(dataNode: JsonNode): Boolean = dataNode.get("monotonic").asBoolean(false)

    private fun getAggregationTemporality(dataNode: JsonNode): AggregationTemporality =
      AggregationTemporality.valueOf(dataNode.get("aggregationTemporality").asString())

    private fun getStartEpoch(dataPointNode: JsonNode): Long = dataPointNode.get("startEpochNanos").asLong()

    private fun getEpoch(dataPointNode: JsonNode): Long = dataPointNode.get("epochNanos").asLong()

    private fun getValue(dataPointNode: JsonNode): JsonNode = dataPointNode.get("value")

    private fun getAttributes(dataPointNode: JsonNode): Attributes = Attributes.builder().apply {
      dataPointNode.get("attributes").properties().forEach { this.put(it.key.toString(), it.value.toString()) }
    }.build()

    private fun getLongPointsData(pointsNode: JsonNode): List<LongPointData> = pointsNode.values().map { dataPoint ->
      val startEpoch = getStartEpoch(dataPoint)
      val epoch = getEpoch(dataPoint)
      val value = getValue(dataPoint)
      val attributes = getAttributes(dataPoint)

      ImmutableLongPointData.create(startEpoch, epoch, attributes, value.asLong())
    }

    private fun getDoublePointsData(pointsNode: JsonNode): List<DoublePointData> = pointsNode.values().map { dataPoint ->
      val startEpoch = getStartEpoch(dataPoint)
      val epoch = getEpoch(dataPoint)
      val value = getValue(dataPoint)
      val attributes = getAttributes(dataPoint)

      ImmutableDoublePointData.create(startEpoch, epoch, attributes, value.asDouble())
    }
  }

  override fun deserialize(jsonParser: JsonParser, ctxt: DeserializationContext): MetricData {
    val node: JsonNode = ctxt.readTree(jsonParser)

    val name = node.get("name").asString()
    val type = node.get("type").asString()
    val unit = node.get("unit").asString()
    val description = node.get("description").asString()

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
        val pointsData = points.values().map { dataPoint ->
          val startEpoch = getStartEpoch(dataPoint)
          val epoch = getEpoch(dataPoint)
          val attributes = getAttributes(dataPoint)

          val max = dataPoint.get("max").asDouble(Double.POSITIVE_INFINITY)
          val sum = dataPoint.get("sum").asDouble()
          val min = dataPoint.get("min").asDouble(Double.NEGATIVE_INFINITY)
          val counts = dataPoint.get("counts").values().map { it.asLong() }
          val boundaries = dataPoint.get("boundaries").values().map { it.asDouble() }

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
