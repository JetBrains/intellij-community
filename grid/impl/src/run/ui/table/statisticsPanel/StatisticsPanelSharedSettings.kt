package com.intellij.database.run.ui.table.statisticsPanel

import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import java.awt.Color

object HtmlSettings {
  const val ALIGN_LEFT = "text-align: left;"
  const val ALIGN_CENTER = "text-align: center;"
  const val ALIGN_RIGHT = "text-align: right;"
  const val ALIGN_TOP = "top"
  val COLOR_GRAY = "color: ${ColorUtil.toHex(HistogramSettings.TOOLTIP_GRAY)};"
  val COLOR_RED = "color: ${ColorUtil.toHex(HistogramSettings.TOOLTIP_RED)};"
  val COLOR_TOOLTIP_FOREGROUND: String get() = "color: ${ColorUtil.toHex(HistogramSettings.TOOLTIP_FOREGROUND)};"
  val COLOR_BAR_FILL_COLOR: String get() = "color: ${ColorUtil.toHex(HistogramSettings.BAR_FILL_COLOR)};"
}

object HistogramSettings {
  val BAR_FILL_COLOR: JBColor = JBColor.namedColor("DataSummary.Chart.barColor", JBColor.BLUE)
  val TOOLTIP_FOREGROUND: JBColor get() = JBColor.namedColor("Editor.ToolTip.foreground", JBColor.WHITE)
  val TOOLTIP_BACKGROUND: JBColor = JBColor.namedColor("Editor.ToolTip.background", JBColor.GRAY)

  val TOOLTIP_RED: Color get() = JBColor.namedColor("Label.errorForeground", JBColor.RED)
  val TOOLTIP_GRAY: Color get() = JBColor.namedColor("ToolTip.infoForeground", JBColor.GRAY)

  const val MAX_LABELS_LENGTH_WITH_BIG_FONT = 30
  const val BIG_FONT_SIZE = 5
  const val SMALL_FONT_SIZE = 4

  const val HISTOGRAM_HEIGHT = 60

  const val BAR_WIDTH = 1.0
  const val HALF_BAR_WIDTH = BAR_WIDTH / 2

  const val LABELS_BOOL_LEFT_X_COORDINATE = 0.0
  const val LABELS_BOOL_RIGHT_X_COORDINATE = 1.0

  const val XLIM_MIN = 0 - HALF_BAR_WIDTH

  const val LABELS_Y_COORDINATE = -0.05
  const val LABELS_FONT_SIZE = 4
  const val VERTICAL_TEXT_ALIGNMENT_TOP = "top"

  const val HORIZONTAL_TEXT_ALIGNMENT_MIDDLE = "middle"
  const val HORIZONTAL_TEXT_ALIGNMENT_LEFT = "left"
  const val HORIZONTAL_TEXT_ALIGNMENT_RIGHT = "right"

  // values for bar heights are normalised: from 0 to 1
  const val YLIM_MAX = 1
  const val YLIM_MIN = -0.2 // not 0, we need space for labels

  const val HISTOGRAM_SIDE_MARGIN = 0

  val LABEL_COLOR_GRAY: Color get() = JBColor.namedColor("ToolTip.infoForeground", JBColor.GRAY)

  const val HISTOGRAM_DASH_LABEL = "\u2014"
}