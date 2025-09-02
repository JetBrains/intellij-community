package com.intellij.database.run.ui.table.statisticsPanel.types

import com.intellij.openapi.util.NlsSafe

/**
 * Represents a single statistics entity with name and value.
 */
data class StatisticsDescriptionUnit(val statisticsName: @NlsSafe String, val statisticsValue: @NlsSafe String)