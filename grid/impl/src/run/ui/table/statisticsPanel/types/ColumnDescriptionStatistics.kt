package com.intellij.database.run.ui.table.statisticsPanel.types

/**
 * Stores information with statistics about a column. Statistics are obtained from the description command.
 */
data class ColumnDescriptionStatistics(val columnStatistics: List<StatisticsDescriptionUnit>)