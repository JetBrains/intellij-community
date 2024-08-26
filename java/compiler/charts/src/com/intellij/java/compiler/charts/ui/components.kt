// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler.charts.ui

import com.intellij.java.compiler.charts.CompilationChartsViewModel.Filter
import com.intellij.java.compiler.charts.CompilationChartsViewModel.Modules.*
import com.intellij.java.compiler.charts.CompilationChartsViewModel.StatisticData
import com.intellij.openapi.util.text.Formats
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.BasicStroke
import java.awt.Color
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

interface ChartComponent {
  fun background(g2d: ChartGraphics, settings: ChartSettings)
  fun component(g2d: ChartGraphics, settings: ChartSettings)
  fun width(settings: ChartSettings): Int = Int.MAX_VALUE
  fun height(): Double
}

class ChartProgress(private val zoom: Zoom, internal val state: ChartModel) : ChartComponent {
  private val model: MutableMap<EventKey, List<Event>> = mutableMapOf()
  var selected: EventKey? = null

  var height: Double = 25.5

  private lateinit var block: ModuleBlock
  private lateinit var background: ModuleBackground

  internal lateinit var clip: Rectangle2D

  fun block(init: ModuleBlock.() -> Unit) {
    block = ModuleBlock().apply(init)
  }

  class ModuleBlock {
    var border: Double = 2.0
    var padding: Double = 1.0
    lateinit var color: (Event) -> Color
    lateinit var outline: (Event) -> Color
    lateinit var selected: (Event) -> Color
  }

  fun background(init: ModuleBackground.() -> Unit) {
    background = ModuleBackground().apply(init)
  }

  class ModuleBackground {
    lateinit var color: (Int) -> Color
  }

  override fun width(settings: ChartSettings): Int {
    model.putAll(state.model.getAndClean())
    var start = clip.x + clip.width
    var end = clip.x

    for ((_, events) in model.asSequence().filter {
      inViewport(it.value.filterIsInstance<StartEvent>().firstOrNull()?.target?.time,
                 it.value.filterIsInstance<FinishEvent>().firstOrNull()?.target?.time,
                 settings,
                 zoom,
                 clip)
    }) {
      val startEvent = events.filterIsInstance<StartEvent>().firstOrNull()
      if (startEvent != null) {
        val rect = getRectangle(startEvent, events.filterIsInstance<FinishEvent>().firstOrNull(), settings)
        start = min(rect.x, start)
        end = max(rect.x + rect.width, end)
      }
    }
    return if (start < end) (end - clip.x).toInt() else 0
  }

  override fun height(): Double = clip.height

  override fun background(g2d: ChartGraphics, settings: ChartSettings) {
    model.putAll(state.model.getAndClean())
    g2d.withColor(settings.background) {
      fill(clip)
    }
    for (row in 0 until state.threads) {
      val cell = Rectangle2D.Double(clip.x, height * row + clip.y, clip.width, height)
      g2d.withColor(background.color(row)) {
        fill(cell)
      }
    }
  }

  override fun component(g2d: ChartGraphics, settings: ChartSettings) {
    g2d.withAntialiasing {
      model.putAll(state.model.getAndClean())
      drawChart(model, settings)
    }
  }

  private fun ChartGraphics.drawChart(
    data: MutableMap<EventKey, List<Event>>,
    settings: ChartSettings) {
    for ((key, events) in data.asSequence()
      .filter { state.filter.test(it.key) }
      .filter {
        inViewport(it.value.filterIsInstance<StartEvent>().firstOrNull()?.target?.time,
                   it.value.filterIsInstance<FinishEvent>().firstOrNull()?.target?.time,
                   settings,
                   zoom,
                   clip)
      }
      .filter { !isSmall(it.value, state) }) {
      val start = events.filterIsInstance<StartEvent>().firstOrNull() ?: continue
      val end = events.filterIsInstance<FinishEvent>().firstOrNull()
      val rect = getRectangle(start, end, settings)

      settings.mouse.module(rect, key, mutableMapOf(
        "duration" to Formats.formatDuration(((end?.target?.time ?: System.nanoTime()) - start.target.time) / 1_000_000),
        "name" to start.target.name,
        "type" to start.target.type,
        "test" to start.target.isTest.toString(),
        "fileBased" to start.target.isFileBased.toString(),
      ))

      withColor(block.color(start)) { // module
        fill(rect)
      }
      withColor(if (selected == key) block.selected(start) else block.outline(start)) { // module border
        draw(rect)
      }
      create().withColor(settings.font.color) {
        withFont(UIUtil.getLabelFont(settings.font.size)) { // name
          clip(rect)
          drawString(" ${start.target.name}", rect.x.toFloat(), (rect.y + (height - block.padding * 2) / 2 + fontMetrics().ascent / 2).toFloat())
        }
      }
    }
  }

  private fun isSmall(events: List<Event>, state: ChartModel): Boolean {
    val filter = state.filter
    if (filter is Filter && filter.text.isEmpty()) {
      val start = events.filterIsInstance<StartEvent>().firstOrNull() ?: return false
      val finish = events.filterIsInstance<FinishEvent>().firstOrNull() ?: return false
      return zoom.toPixels(finish.target.time) - zoom.toPixels(start.target.time) < 2
    }
    else {
      return false
    }
  }

  private fun getRectangle(start: StartEvent, end: FinishEvent?, settings: ChartSettings): Rectangle2D {
    val x0 = zoom.toPixels(start.target.time - settings.duration.from)
    val x1 = zoom.toPixels((end?.target?.time ?: System.nanoTime()) - settings.duration.from)
    return Rectangle2D.Double(x0, (start.threadNumber * height), x1 - x0, height)
  }
}

class ChartUsage(private val zoom: Zoom, private val name: String, internal val state: UsageModel) : ChartComponent {
  private val model: NavigableSet<StatisticData> = TreeSet()

  lateinit var unit: String
  lateinit var color: UsageColor

  internal lateinit var clip: Rectangle2D

  fun color(init: UsageColor.() -> Unit) {
    color = UsageColor().apply(init)
  }

  class UsageColor {
    lateinit var background: JBColor
    lateinit var border: JBColor
  }

  override fun background(g2d: ChartGraphics, settings: ChartSettings) {
    model.addAll(state.model.getAndClean())
    g2d.withColor(settings.background) {
      fill(clip)
    }
    g2d.withColor(settings.line.color) {
      draw(Line2D.Double(0.0, clip.y, clip.width, clip.y))
    }
  }

  override fun component(g2d: ChartGraphics, settings: ChartSettings) {
    model.addAll(state.model.getAndClean())
    drawUsageChart(model, settings, g2d)
  }

  override fun width(settings: ChartSettings): Int {
    model.addAll(state.model.getAndClean())
    if (model.isEmpty()) return 0
    val (end, _) = getXY(model.last(), settings, 0.0, 0.0)
    return (end - clip.x).toInt()
  }

  override fun height(): Double = clip.height

  private fun drawUsageChart(
    data: NavigableSet<StatisticData>,
    settings: ChartSettings,
    g2d: ChartGraphics,
  ): Boolean {
    val filtered = filterData(data, settings)
    val path = path(filtered, settings) ?: return true
    val border = border(filtered, settings) ?: return true

    g2d.withColor(color.background) {
      fill(path)
    }
    g2d.withStroke(BasicStroke(USAGE_BORDER)) {
      withColor(color.border) {
        draw(border)
      }
    }
    return false
  }

  private fun filterData(data: NavigableSet<StatisticData>, settings: ChartSettings): NavigableSet<StatisticData> {
    val filtered: NavigableSet<StatisticData> = TreeSet(data.filter { statistic -> inViewport(statistic.time, statistic.time, settings, zoom, clip) })
    if (filtered.isEmpty()) return filtered

    (0..5).forEach { i ->
      data.lower(filtered.first())?.let { first -> filtered.add(first) }
      data.higher(filtered.last())?.let { last -> filtered.add(last) }
    }
    return filtered
  }

  private fun border(data: NavigableSet<StatisticData>, settings: ChartSettings): Path2D? =
    path(data, settings, { _, _, _, _ -> }, { _, _, _ -> })

  private fun path(data: NavigableSet<StatisticData>, settings: ChartSettings): Path2D? {
    val setFirstPoint: (Path2D, Double, Double, Double) -> Unit = { path, x0, y0, data0 ->
      path.moveTo(x0, y0)
      path.moveTo(x0, data0)
    }
    val setLastPoint: (Path2D, Double, Double) -> Unit = { path, x0, y0 ->
      path.currentPoint?.let { last ->
        path.lineTo(last.x, y0)
        path.lineTo(x0, y0)
      }
      path.closePath()
    }
    return path(data, settings, setFirstPoint, setLastPoint)
  }

  private fun path(data: NavigableSet<StatisticData>, settings: ChartSettings,
                   before: (Path2D, Double, Double, Double) -> Unit,
                   after: (Path2D, Double, Double) -> Unit): Path2D? {
    if (data.isEmpty()) return null
    val neighborhood = DoubleArray(8) { Double.NaN }
    val border = 5
    val height = clip.height - border
    val y0 = clip.y + height + border
    val path = Path2D.Double()

    val (x0, data0) = getXY(data.first(), settings, y0, height)
    before(path, x0, y0, data0)

    data.forEach { statistic ->
      val (px, py) = getXY(statistic, settings, y0, height)
      path.currentPoint ?: path.moveTo(px, py) // if the first point
      neighborhood.shiftLeftByTwo(px, py)
      path.curveTo(neighborhood)
    }

    after(path, x0, y0)
    return path
  }

  private fun getXY(statistic: StatisticData, settings: ChartSettings, y0: Double, height: Double): Pair<Double, Double> =
    zoom.toPixels(statistic.time - settings.duration.from) to
      y0 - (statistic.data.toDouble() / state.maximum * height)

  private fun DoubleArray.shiftLeftByTwo(first: Double, second: Double) {
    for (j in 2 until size) {
      this[j - 2] = this[j]
    }
    this[size - 2] = first
    this[size - 1] = second
  }
}

class ChartAxis(private val zoom: Zoom) : ChartComponent {
  var stroke: FloatArray = floatArrayOf(5f, 5f)
  var distance: Int = 250
  var count: Int = 10
  var height: Double = 0.0
  var padding: Int = 2

  internal lateinit var clip: Rectangle2D

  override fun background(g2d: ChartGraphics, settings: ChartSettings) {
    g2d.withColor(settings.background) {
      fill(clip)
    }
    g2d.withColor(settings.line.color) {
      draw(Line2D.Double(clip.x, clip.y, clip.x + clip.width, clip.y))
    }
  }

  override fun component(g2d: ChartGraphics, settings: ChartSettings) {
    g2d.withAntialiasing {
      val size = UIUtil.getFontSize(settings.font.size) + padding

      val from = (clip.x.toInt() / distance).toInt() * distance
      val to = from + clip.width.toInt() + clip.x.toInt() % distance

      withColor(COLOR_LINE) {
        for (x in from..to step distance) {
          // big axis
          withStroke(BasicStroke(1.5F, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f, stroke, 0.0f)) {
            draw(Line2D.Double(x.toDouble(), 0.0, x.toDouble(), clip.y))
          }
          for (x1 in x..x + distance step distance / count) {
            // additional axis
            draw(Line2D.Double(x1.toDouble(), clip.y, x1.toDouble(), clip.y + (size / 2)))
          }
        }
      }
      withColor(settings.font.color) {
        val step = zoom.toDuration(distance)
        val trim = if (TimeUnit.NANOSECONDS.toMinutes(step) > 2) 60_000
        else if (TimeUnit.NANOSECONDS.toMinutes(step) >= 1) 1_000
        else if (TimeUnit.NANOSECONDS.toSeconds(step) > 2) 1_000
        else 1

        withFont(UIUtil.getLabelFont(settings.font.size)) {
          for (x in from..to step distance) {
            val time = Formats.formatDuration((TimeUnit.NANOSECONDS.toMillis(zoom.toDuration(x)) / trim) * trim)
            drawString(time, x + padding, (clip.y + size).toInt())
          }
        }
      }
    }
  }

  override fun height(): Double = clip.height
}