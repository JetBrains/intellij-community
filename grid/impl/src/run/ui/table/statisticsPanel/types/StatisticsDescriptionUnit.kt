package com.intellij.database.run.ui.table.statisticsPanel.types

import com.intellij.openapi.util.NlsSafe
import kotlinx.serialization.Serializable

/**
 * Represents a single statistics entity with name and value.
 */
@Serializable
data class StatisticsDescriptionUnit(val statisticsName: @NlsSafe String, val statisticsValue: @NlsSafe String)