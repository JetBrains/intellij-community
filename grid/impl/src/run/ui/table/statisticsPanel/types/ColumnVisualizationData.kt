package com.intellij.database.run.ui.table.statisticsPanel.types

data class ColumnVisualizationDataTooltip(
  val anchor: String,
  val line: String,
  val format: List<Format>,
) {
  data class Format(val field: String, val format: String)
}

/**
 * Represents the data needed for visualization in [com.intellij.scientific.py.tables.panel.statistics.PyStatisticsTableHeader].
 */
data class ColumnVisualizationData(
  val visualisationType: ColumnVisualisationType,
  val data: Map<String, List<*>>?,
  val tooltips: ColumnVisualizationDataTooltip? = null,
  val axisXLabels: ColumnVisualizationDataAxisXLabels? = null,
)