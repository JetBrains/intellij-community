package com.intellij.database.run.ui.table.statisticsPanel.types

import com.intellij.openapi.util.NlsSafe

/**
 * Represents a single statistics with methods to get name and value.
 */
interface StatisticsValueUnit {
  fun getName(): @NlsSafe String
  fun getValue(): @NlsSafe String
}
