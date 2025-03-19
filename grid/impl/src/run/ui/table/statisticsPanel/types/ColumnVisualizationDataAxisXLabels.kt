package com.intellij.database.run.ui.table.statisticsPanel.types

/**
 * This data class is used to plot visualization in [PyStatisticsTableHeader].
 *
 * For numerical columns, it represents the labels for the X-axis.
 * These labels correspond to the MIN and MAX of column values in a column and are rendered below a histogram.
 */
data class ColumnVisualizationDataAxisXLabels(val leftValue: String,
                                              val rightValue: String,
                                              val xCoordLeft: Double,
                                              val xCoordRight: Double,
                                              val hjustLeft: String,
                                              val hjustRight: String,
                                              val fontSize: Int)