package com.intellij.database.run.ui.table.statisticsPanel.types

import kotlinx.serialization.Serializable

@Serializable
data class ColumnVisualizationDataTooltip(
  val anchor: String,
  val line: String,
  val format: List<Format>,
) {
  @Serializable
  data class Format(val field: String, val format: String)
}

/**
 * Represents the data needed for visualization in [com.intellij.scientific.py.tables.panel.statistics.PyStatisticsTableHeader].
 */
@Serializable
sealed class ColumnVisualizationData() {
  abstract val visualisationType: ColumnVisualisationType
}

@Serializable
data class HistogramData(
  // val x: List<String>,  xLabel contains same values
  val barHeights: List<Double>,
  val xLabel: List<String>,
  val yLabel: List<Int>,
  var percentageList: List<Int>? = null,
) {
  fun toMap(): Map<String, List<Any>> {
    return mutableMapOf("x" to xLabel, "xLabel" to xLabel, "yLabel" to yLabel, "y" to barHeights).apply {
      if(percentageList != null) this["percentage"] = percentageList!!
    }
  }
}

@Serializable
class ColumnVisualizationDataHistogram(
  val data: HistogramData,
  val tooltips: ColumnVisualizationDataTooltip? = null,
  val axisXLabels: ColumnVisualizationDataAxisXLabels? = null,
) : ColumnVisualizationData() {
  override val visualisationType: ColumnVisualisationType
    get() = ColumnVisualisationType.HISTOGRAM
}

@Serializable
class ColumnVisualizationDataPercentage(
  val percentageMap: Map<String, List<String?>>,
) : ColumnVisualizationData() {
  override val visualisationType: ColumnVisualisationType
    get() = ColumnVisualisationType.PERCENTAGE
}

@Serializable
class ColumnVisualizationDataUnique(
  val numberOfUnique: String,
) : ColumnVisualizationData() {
  override val visualisationType: ColumnVisualisationType
    get() = ColumnVisualisationType.UNIQUE
}