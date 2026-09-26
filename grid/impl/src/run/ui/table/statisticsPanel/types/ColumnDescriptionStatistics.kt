package com.intellij.database.run.ui.table.statisticsPanel.types

import kotlinx.serialization.Serializable

/**
 * Stores information with statistics about a column. Statistics are obtained from the description command.
 */
@Serializable
data class ColumnDescriptionStatistics(val columnStatistics: List<StatisticsDescriptionUnit>)