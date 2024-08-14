// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler.charts.ui

import com.intellij.java.compiler.charts.CompilationChartsViewModel.Modules.*
import com.intellij.java.compiler.charts.CompilationChartsViewModel.StatisticData
import com.intellij.openapi.util.text.Formats
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.util.*
import java.util.concurrent.TimeUnit

interface ChartComponent {
  fun background(g2d: Graphics2D, settings: ChartSettings)
  fun component(g2d: Graphics2D, settings: ChartSettings)
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

  override fun background(g2d: Graphics2D, settings: ChartSettings) {
    model.putAll(state.model.getAndClean())
    g2d.withColor(settings.background) {
      fill(this@ChartProgress.clip)
    }
    for (row in 0 until state.threads) {
      val cell = Rectangle2D.Double(clip.x, height * row + clip.y, clip.width, height)
      g2d.withColor(background.color(row)) {
        fill(cell)
      }
    }
  }

  override fun component(g2d: Graphics2D, settings: ChartSettings) {
    settings.mouse.clear()

    g2d.withAntialiasing {
      model.putAll(state.model.getAndClean())
      drawChart(model, settings, g2d)
    }
  }

  private fun Graphics2D.drawChart(
    data: MutableMap<EventKey, List<Event>>,
    settings: ChartSettings,
    g2d: Graphics2D,
  ) {
    for ((key, events) in data.asSequence()
      .filter { state.filter.test(it.key) }
      .filter {
        inViewport(it.value.filterIsInstance<StartEvent>().firstOrNull()?.target?.time,
                   it.value.filterIsInstance<FinishEvent>().firstOrNull()?.target?.time,
                   settings,
                   zoom,
                   this@ChartProgress.clip)
      }
      .filter { !isSmall(it.value) }) {
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
      (g2d.create() as Graphics2D).withColor(settings.font.color) {
        withFont(UIUtil.getLabelFont(settings.font.size)) { // name
          clip(rect)
          drawString(" ${start.target.name}", rect.x.toFloat(), (rect.y + (this@ChartProgress.height - block.padding * 2) / 2 + fontMetrics.ascent / 2).toFloat())
        }
      }
    }
  }

  private fun isSmall(events: List<Event>): Boolean {
    val start = events.filterIsInstance<StartEvent>().firstOrNull() ?: return false
    val finish = events.filterIsInstance<FinishEvent>().firstOrNull() ?: return false
    return zoom.toPixels(finish.target.time) - zoom.toPixels(start.target.time) < 2
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

  override fun background(g2d: Graphics2D, settings: ChartSettings) {
    model.addAll(state.model.getAndClean())
    g2d.withColor(settings.background) {
      fill(this@ChartUsage.clip)
    }
    g2d.withColor(settings.line.color) {
      draw(Line2D.Double(0.0, this@ChartUsage.clip.y, this@ChartUsage.clip.width, this@ChartUsage.clip.y))
    }
  }

  override fun component(g2d: Graphics2D, settings: ChartSettings) {
    model.addAll(state.model.getAndClean())
    drawUsageChart(model, settings, g2d)
  }

  private fun drawUsageChart(data: NavigableSet<StatisticData>,
                             settings: ChartSettings,
                             g2d: Graphics2D): Boolean {
    if (data.isEmpty()) return true
    val path = path(data, settings)
    g2d.withStroke(BasicStroke(USAGE_BORDER)) {
      withColor(this@ChartUsage.color.border) {
        draw(path)
      }
      withColor(this@ChartUsage.color.background) {
        fill(path)
      }
    }
    return false
  }

  private fun path(data: NavigableSet<StatisticData>, settings: ChartSettings): Path2D {
    val neighborhood = DoubleArray(8) { Double.NaN }
    val border = 5
    val height = clip.height - border
    val x0 = this@ChartUsage.clip.x
    val y0 = clip.y + height + border

    val path = Path2D.Double()
    path.moveTo(x0, y0)

    var cursor: StatisticData? = null
    data.forEach { statistic ->
      if (inViewport(statistic.time, statistic.time, settings, zoom, clip)) {
        if (cursor == null) {
          // prev point
          data.lower(statistic)?.let { lower ->
            getXY(lower, settings, y0, height).let { (px, py) ->
              neighborhood.shiftLeftByTwo(px, py)
              path.curveTo(neighborhood)
            }
          }
        }
        getXY(statistic, settings, y0, height).let { (px, py) ->
          neighborhood.shiftLeftByTwo(px, py)
          path.curveTo(neighborhood)
        }
        cursor = statistic
      }
    }
    // next point
    if (cursor != null) data.higher(cursor)?.let { higher ->
      getXY(higher, settings, y0, height).let { (px, py) ->
        neighborhood.shiftLeftByTwo(px, py)
        path.curveTo(neighborhood)
      }
    }

    neighborhood.shiftLeftByTwo(Double.NaN, Double.NaN)
    path.curveTo(neighborhood)

    path.currentPoint?.let { last ->
      path.lineTo(last.x, y0)
      path.lineTo(x0, y0)
    }
    path.closePath()

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

  override fun background(g2d: Graphics2D, settings: ChartSettings) {
    g2d.withColor(settings.background) {
      fill(this@ChartAxis.clip)
    }
    g2d.withColor(settings.line.color) {
      draw(Line2D.Double(this@ChartAxis.clip.x, this@ChartAxis.clip.y, this@ChartAxis.clip.x + this@ChartAxis.clip.width, this@ChartAxis.clip.y))
    }
  }

  override fun component(g2d: Graphics2D, settings: ChartSettings) {
    g2d.withAntialiasing {
      val size = UIUtil.getFontSize(settings.font.size) + padding

      val from = (this@ChartAxis.clip.x.toInt() / distance).toInt() * distance
      val to = from + this@ChartAxis.clip.width.toInt() + this@ChartAxis.clip.x.toInt() % distance

      withColor(COLOR_LINE) {
        for (x in from..to step distance) {
          // big axis
          withStroke(BasicStroke(1.5F, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f, this@ChartAxis.stroke, 0.0f)) {
            draw(Line2D.Double(x.toDouble(), 0.0, x.toDouble(), this@ChartAxis.clip.y))
          }
          for (x1 in x..x + distance step distance / count) {
            // additional axis
            draw(Line2D.Double(x1.toDouble(), this@ChartAxis.clip.y, x1.toDouble(), this@ChartAxis.clip.y + (size / 2)))
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
            drawString(time, x + padding, (this@ChartAxis.clip.y + size).toInt())
          }
        }
      }
    }
  }
}