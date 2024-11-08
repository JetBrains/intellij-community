// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler.charts.ui

import com.intellij.ide.nls.NlsMessages
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
import kotlin.math.roundToInt

interface ChartComponent {
  fun background(g2d: ChartGraphics, settings: ChartSettings)
  fun component(g2d: ChartGraphics, settings: ChartSettings)
  fun width(settings: ChartSettings): Int = Int.MAX_VALUE
  fun height(): Double
}

class ChartProgress(private val zoom: Zoom, internal val state: ChartModel, threadAddedEvent: () -> Unit) : ChartComponent {
  private val model: ModulesCollection = ModulesCollection(threadAddedEvent)
  var selected: EventKey? = null

  var height: Double = 25.5

  private lateinit var block: ModuleBlock
  private lateinit var background: ModuleBackground

  internal lateinit var bracket: Rectangle2D

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
    model.addAll(state.model.getAndClean())
    var start = bracket.x + bracket.width
    var end = bracket.x

    model.list().forEachIndexed { thread, events ->
      events.forEach { event ->
        if (compareWithViewport(event.start.target.time,
                                event.finish?.target?.time,
                                settings, zoom, bracket) == 0) {
          val rect = getRectangle(event, thread, settings)
          start = min(rect.x, start)
          end = max(rect.x + rect.width, end)
        }
      }
    }
    return if (start < end) (end - bracket.x).roundToInt() else 0
  }

  override fun height(): Double = bracket.height
  fun rows(): Int = model.list().size

  override fun background(g2d: ChartGraphics, settings: ChartSettings) {
    model.addAll(state.model.getAndClean())
    g2d.withColor(settings.background) {
      fill(bracket)
    }
    model.list().forEachIndexed { thread, _ ->
      val cell = Rectangle2D.Double(bracket.x, height * thread + bracket.y, bracket.width, height)
      g2d.withColor(background.color(thread)) {
        fill(cell)
      }
    }
  }

  override fun component(g2d: ChartGraphics, settings: ChartSettings) {
    g2d.withAntialiasing {
      model.addAll(state.model.getAndClean())
      drawChart(model, settings)
    }
  }

  private fun ChartGraphics.drawChart(
    data: ModulesCollection,
    settings: ChartSettings,
  ) {
    data.list().forEachIndexed { thread, events ->
      events.forEach { event ->
        if (state.filter.test(event.key) &&
            compareWithViewport(event.start.target.time,
                                event.finish?.target?.time,
                                settings, zoom, bracket) == 0 &&
            !isSmall(event, state)) {


          val rect = getRectangle(event, thread, settings)

          settings.mouse.module(rect, event.key, mutableMapOf(
            "duration" to NlsMessages.formatDurationApproximate(((event.finish?.target?.time ?: state.currentTime) - event.start.target.time) / 1_000_000),
            "name" to event.start.target.name,
            "type" to event.start.target.type,
            "test" to event.start.target.isTest.toString(),
            "fileBased" to event.start.target.isFileBased.toString(),
          ))

          withColor(block.color(event.start)) { // module
            fill(rect)
          }
          withColor(if (selected == event.key) block.selected(event.start) else block.outline(event.start)) { // module border
            draw(rect)
          }
          create().withColor(settings.font.color) {
            withFont(UIUtil.getLabelFont(settings.font.size)) { // name
              clip(rect)
              drawString(" ${event.start.target.name}", rect.x.toFloat(), (rect.y + (height - block.padding * 2) / 2 + fontMetrics().ascent / 2).toFloat())
            }
          }

        }
      }
    }
  }

  private fun isSmall(event: ProgressEvent, state: ChartModel): Boolean {
    val filter = state.filter
    if (filter is Filter && filter.text.isEmpty()) {
      val finish = event.finish ?: return false
      return zoom.toPixels(finish.target.time) - zoom.toPixels(event.start.target.time) < 2
    }
    else {
      return false
    }
  }

  private fun getRectangle(event: ProgressEvent, thread: Int, settings: ChartSettings): Rectangle2D {
    val x0 = zoom.toPixels(event.start.target.time - settings.duration.from)
    val x1 = zoom.toPixels((event.finish?.target?.time ?: state.currentTime) - settings.duration.from)
    return Rectangle2D.Double(x0, (thread * height), x1 - x0, height)
  }

  private class ModulesCollection(private val threadAddedEvent: () -> Unit) {
    private val index = HashMap<EventKey, Pair<Int, Int>>()
    private val events: MutableList<MutableList<ProgressEvent>> = mutableListOf()

    fun add(event: ProgressEvent) {
      index[event.key]?.let { (thread, position) ->
        events[thread][position] = event
        return
      }

      for ((idx, thread) in events.withIndex()) {
        if (canBeAdded(thread, event)) {
          thread.add(event)
          index[event.key] = Pair(idx, thread.size - 1)
          return
        }
      }
      events.add(mutableListOf(event))
      index[event.key] = Pair(events.size - 1, 0)
      threadAddedEvent()
    }

    fun addAll(events: Map<EventKey, List<Event>>) = events
      .map { (key, data) ->
        val start = data.filterIsInstance<StartEvent>().firstOrNull()
        val finish = data.filterIsInstance<FinishEvent>().firstOrNull()
        if (start != null) ProgressEvent(key, start, finish) else null
      }.filterNotNull()
      .sortedWith(compareBy({ it.finish == null },
                            { it.start.target.time },
                            { (it.finish?.target?.time ?: 0) * -1 }))
      .forEach { event ->
        add(event)
      }

    fun list(): List<List<ProgressEvent>> = events

    private fun canBeAdded(thread: MutableList<ProgressEvent>, event: ProgressEvent): Boolean =
      thread.all { !it.overlaps(event) }
  }

  private data class ProgressEvent(val key: EventKey, val start: StartEvent, val finish: FinishEvent?) {
    fun overlaps(other: ProgressEvent): Boolean {
      return this.start.target.time <= (other.finish?.target?.time ?: Long.MAX_VALUE) &&
             other.start.target.time <= (this.finish?.target?.time ?: Long.MAX_VALUE)
    }
  }
}

class ChartUsage(private val zoom: Zoom, private val name: String, internal val state: UsageModel) : ChartComponent {
  internal val model: NavigableSet<StatisticData> = TreeSet()

  lateinit var format: (StatisticData) -> String
  lateinit var color: UsageColor

  internal lateinit var bracket: Rectangle2D

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
      fill(bracket)
    }
    g2d.withColor(settings.line.color) {
      draw(Line2D.Double(bracket.x, bracket.y, bracket.x + bracket.width, bracket.y))
    }
  }

  override fun component(g2d: ChartGraphics, settings: ChartSettings) {
    model.addAll(state.model.getAndClean())

    val filtered = filterData(model, settings)
    val path = path(filtered, settings) ?: return
    val border = border(filtered, settings) ?: return

    g2d.withColor(color.background) {
      fill(path)
    }
    g2d.withColor(color.border) {
      g2d.withStroke(BasicStroke(Settings.Usage.BORDER)) {
        draw(border)
      }
    }
  }

  fun drawPoint(g2d: ChartGraphics, data: StatisticData, settings: ChartSettings) {
    val border = 5
    val height = bracket.height - border
    val y0 = bracket.y + height + border

    val radius = 4
    val (x, y) = getXY(data, settings, y0, height)
    g2d.withColor(color.border) {
      fillOval(x.roundToInt() - radius, y.roundToInt() - radius, radius * 2, radius * 2)
    }
    g2d.withColor(color.background) {
      drawOval(x.roundToInt() - radius, y.roundToInt() - radius, radius * 2, radius * 2)
    }
    g2d.withColor(settings.font.color) {
      val text = format(data)
      val bounds = getStringBounds(text)
      drawString(text, x.roundToInt() - bounds.width.roundToInt() / 2, maxOf(y.roundToInt() - bounds.height.roundToInt(), bounds.height.roundToInt() + 30))
    }
  }

  override fun width(settings: ChartSettings): Int {
    model.addAll(state.model.getAndClean())
    if (model.isEmpty()) return 0
    val (end, _) = getXY(model.last(), settings, 0.0, 0.0)
    return (end - bracket.x).roundToInt()
  }

  override fun height(): Double = bracket.height

  private fun filterData(data: NavigableSet<StatisticData>, settings: ChartSettings): NavigableSet<StatisticData> {
    val filtered = TreeSet<StatisticData>()
    var before: StatisticData? = null
    var after: StatisticData? = null
    for (statistic in data) {
      when(compareWithViewport(statistic.time, statistic.time, settings, zoom, bracket)) {
        0 -> filtered.add(statistic)
        -1 -> before = statistic
        1 -> after = statistic
      }
      if (after != null) break
    }
    if (before != null) filtered.add(before)
    if (after != null) filtered.add(after)
    if (filtered.isNotEmpty()) {
      (0..4).forEach { i ->
        data.lower(filtered.first())?.let { first -> filtered.add(first) }
        data.higher(filtered.last())?.let { last -> filtered.add(last) }
      }
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

  private fun path(
    data: NavigableSet<StatisticData>, settings: ChartSettings,
    before: (Path2D, Double, Double, Double) -> Unit,
    after: (Path2D, Double, Double) -> Unit,
  ): Path2D? {
    if (data.isEmpty()) return null
    val neighborhood = DoubleArray(8) { Double.NaN }
    val border = 5
    val height = bracket.height - border
    val y0 = bracket.y + height + border
    val path = Path2D.Double()

    val (x0, data0) = getXY(data.first(), settings, y0, height)
    before(path, x0, y0, data0)

    data.forEach { statistic ->
      val (px, py) = getXY(statistic, settings, y0, height)
      path.currentPoint ?: path.moveTo(px, py) // if the first point
      neighborhood.shiftLeftByTwo(px, py)
      path.curveTo(neighborhood)
    }
    neighborhood.shiftLeftByTwo(Double.NaN, Double.NaN)
    path.curveTo(neighborhood)

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

  internal lateinit var bracket: Rectangle2D

  override fun background(g2d: ChartGraphics, settings: ChartSettings) {
    g2d.withColor(settings.background) {
      fill(bracket)
    }
    g2d.withColor(settings.line.color) {
      draw(Line2D.Double(bracket.x, bracket.y, bracket.x + bracket.width, bracket.y))
    }
  }

  override fun component(g2d: ChartGraphics, settings: ChartSettings) {
    g2d.withAntialiasing {
      val size = UIUtil.getFontSize(settings.font.size) + padding

      val from = (bracket.x.roundToInt() / distance) * distance
      val to = from + bracket.width.roundToInt() + bracket.x.roundToInt() % distance

      withColor(Colors.LINE) {
        for (x in from..to step distance) {
          // big axis
          withStroke(BasicStroke(1.5F, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0f, this@ChartAxis.stroke, 0.0f)) {
            draw(Line2D.Double(x.toDouble(), 0.0, x.toDouble(), bracket.y))
          }
          for (x1 in x..x + distance step distance / count) {
            // additional axis
            draw(Line2D.Double(x1.toDouble(), bracket.y, x1.toDouble(), bracket.y + (size / 2)))
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
            drawString(time, x + padding, (bracket.y + size).roundToInt())
          }
        }
      }
    }
  }

  override fun height(): Double = bracket.height
}