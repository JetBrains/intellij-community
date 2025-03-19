package com.intellij.database.run.ui.table.statisticsPanel.types

enum class ColumnVisualisationType {
  HISTOGRAM,
  UNIQUE,
  PERCENTAGE;

  companion object {
    fun fromPythonString(name: String): ColumnVisualisationType? =
      when (name) {
        "histogram" -> HISTOGRAM
        "unique" -> UNIQUE
        "percentage" -> PERCENTAGE
        else -> null
      }
  }
}