// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere.setesting

import com.intellij.ui.JBColor
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI
import java.awt.*
import java.util.stream.Collectors
import javax.swing.JComponent
import kotlin.math.ceil

private const val TIME_AXIS_NUMBERS_COUNT = 4
private const val NUMBER_AXIS_NUMBERS_COUNT = 5

class ResultsGraph(private val contributorName: String, private val resultTimings: List<Long>, private val maxShownTime: Long) : JComponent() {

  private var groupingInterval = 100

  init {
    isOpaque = true
    background = JBColor.white
    border = JBUI.Borders.customLine(JBColor.gray)
  }

  override fun getPreferredSize(): Dimension = Dimension(300, 200)

  override fun getMinimumSize(): Dimension = Dimension(150, 100)

  fun setGroupingInterval(interval: Int) {
    groupingInterval = interval
    revalidate()
    repaint()
  }

  override fun paint(g: Graphics) {
    if (isOpaque) paintBackground(g)
    paintBorder(g)
    val rect = Rectangle(0, 0 , width, height)
    JBInsets.removeFrom(rect, insets)
    paintContent(g, rect)
  }

  private fun paintContent(g: Graphics, rect: Rectangle) {
    val g2 = g.create()
    try {
      val fontMetrics = g2.fontMetrics
      val nameBaseLine = rect.y + fontMetrics.ascent + fontMetrics.leading
      paintName(contributorName, Point(0, nameBaseLine), g2)

      val nameHeight = fontMetrics.leading + fontMetrics.ascent + fontMetrics.descent + 10

      val countAxisWith = fontMetrics.stringWidth("000") + 4
      val timeAxisHeight = fontMetrics.ascent + 4

      val countAxisRect = Rectangle(rect.x, rect.y, countAxisWith, rect.height)
      val timeAxisRect = Rectangle(rect.x, rect.maxY.toInt() - timeAxisHeight, rect.width, timeAxisHeight)
      val graphRect = Rectangle(rect.x, rect.y, rect.width, rect.height)
      JBInsets.removeFrom(countAxisRect, JBUI.insets(nameHeight, 0, timeAxisHeight, 0))
      JBInsets.removeFrom(timeAxisRect, JBUI.insetsLeft(countAxisWith))
      JBInsets.removeFrom(graphRect, JBUI.insets(nameHeight, countAxisWith, timeAxisHeight, 0))
      val columnWidth = (graphRect.width / (maxShownTime / groupingInterval + 1)).toInt()

      paintCountAxis(g2, fontMetrics, countAxisRect)
      paintTimeAxis(g2, fontMetrics, timeAxisRect, columnWidth)
      paintGraph(resultTimings, graphRect, g2, columnWidth)
    }
    finally {
      g2.dispose()
    }
  }

  private fun paintCountAxis(g: Graphics, fontMetrics: FontMetrics, rect: Rectangle) {
    g.color = JBColor.gray
    g.drawLine(rect.maxX.toInt(), rect.minY.toInt(), rect.maxX.toInt(), rect.maxY.toInt())

    val intervals = resultTimings.stream().collect(Collectors.groupingBy({ it / groupingInterval }, Collectors.counting()))
    val maxCount = intervals.values.maxOrNull() ?: return
    val step = ceil(maxCount.toDouble() / NUMBER_AXIS_NUMBERS_COUNT).toLong()

    for (i in step..maxCount step step) {
      val text = i.toString()
      val stringWidth = fontMetrics.stringWidth(text)
      val yPoint = (rect.maxY - i.toDouble() / maxCount * rect.height).toInt() + fontMetrics.ascent
      val xPoint = (rect.maxX - 4 - stringWidth).toInt()
      g.drawString(text, xPoint, yPoint)
    }
  }

  private fun paintTimeAxis(g2: Graphics, fontMetrics: FontMetrics, rect: Rectangle, columnWidth: Int) {
    g2.color = JBColor.gray
    g2.drawLine(rect.x, rect.y, rect.maxX.toInt(), rect.y)
    val y = rect.y + fontMetrics.ascent + 3

    val columnCount = ceil(maxShownTime.toDouble() / groupingInterval).toInt()
    var axisStep = ceil(columnCount.toDouble() / TIME_AXIS_NUMBERS_COUNT).toInt()
    for (i in 0..columnCount step axisStep) {
      val x = i * columnWidth + rect.x
      val text = (i * groupingInterval).toString()
      g2.drawString(text, x, y)
    }
  }

  private fun paintName(contributorName: String, point: Point, g: Graphics) {
    val g2 = g.create()
    try {
      g2.color = foreground
      g2.drawString(contributorName, point.x, point.y)
    }
    finally {
      g2.dispose()
    }
  }

  private fun paintBackground(g: Graphics) {
    val g2 = g.create()
    try {
      g2.color = background
      g2.fillRect(0, 0, width, height)
    }
    finally {
      g2.dispose()
    }
  }

  private fun paintGraph(timings: List<Long>, rect: Rectangle, g: Graphics, columnWidth: Int) {
    val g2 = g.create()
    try {
      if (timings.isEmpty()) {
        printNoData(g2, rect)
        return
      }
      g2.color = JBColor.blue
      val intervals = timings.stream().collect(Collectors.groupingBy({ it / groupingInterval }, Collectors.counting()))
      val maxIntervalItems = intervals.values.max()
      val bottomLine = rect.maxY.toInt()
      for (intervalItems in intervals) {
        val columnHeight = (rect.height * 1.0 * intervalItems.value / maxIntervalItems).toInt()
        val point = Point(rect.x + (intervalItems.key * columnWidth).toInt(), (bottomLine - columnHeight))
        g2.fillRect(point.x, point.y, columnWidth, columnHeight)
      }
    }
    finally {
      g2.dispose()
    }
  }

  private fun printNoData(g: Graphics, rect: Rectangle) {
    g.color = JBColor.gray
    val str = "No elements"
    val stringBounds = g.fontMetrics.getStringBounds(str, g)
    g.drawString(str, rect.x + (rect.width - stringBounds.width.toInt()) / 2, rect.y + (rect.height - stringBounds.height.toInt()) / 2)
  }
}