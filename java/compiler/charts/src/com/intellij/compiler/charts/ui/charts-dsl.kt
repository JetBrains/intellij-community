// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.charts.ui

import com.intellij.compiler.charts.CompilationChartsBundle
import com.intellij.compiler.charts.CompilationChartsViewModel
import com.intellij.compiler.charts.CompilationChartsViewModel.*
import com.intellij.compiler.charts.CompilationChartsViewModel.CpuMemoryStatisticsType.MEMORY
import com.intellij.compiler.charts.CompilationChartsViewModel.Modules.*
import com.intellij.openapi.util.text.Formats
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.UIUtil.FontSize
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.function.Predicate
import javax.swing.JLabel
import javax.swing.JPopupMenu
import javax.swing.JViewport
import kotlin.math.max
import kotlin.math.min

enum class RenderType {
  FULL,
  DIFF
}

fun charts(vm: CompilationChartsViewModel, zoom: Zoom, viewport: JViewport, init: Charts.() -> Unit): Charts {
  return Charts(vm, zoom, viewport).apply(init)
}

class Charts(private val vm: CompilationChartsViewModel, private val zoom: Zoom, private val viewport: JViewport) {
  private val model: DataModel = DataModel(this)
  internal var area: Area = Area(viewport.x.toDouble(), viewport.y.toDouble(), viewport.width.toDouble(), viewport.height.toDouble())
  internal val progress: ChartProgress = ChartProgress(zoom, model.chart)
  internal lateinit var usage: ChartUsage
  internal lateinit var axis: ChartAxis
  internal var settings: ChartSettings = ChartSettings()

  fun settings(init: ChartSettings.() -> Unit) {
    settings = ChartSettings().apply(init)
  }

  fun progress(init: ChartProgress.() -> Unit) {
    progress.apply(init)
  }

  fun usage(init: ChartUsage.() -> Unit) {
    usage.apply(init)
  }

  fun axis(init: ChartAxis.() -> Unit) {
    axis = ChartAxis(zoom).apply(init)
  }

  fun draw(g2d: Graphics2D, type: RenderType, init: Charts.() -> Unit) {
    init()
    val components = listOf(progress, usage, axis)
    components.forEach { it.background(g2d, type, settings) }
    components.forEach { it.component(g2d, type, settings) }
  }

  fun width(): Double = axis.clip.width
  fun height(): Double = axis.clip.run { y + height }

  fun update(init: Charts.() -> Unit) {
    init()
  }

  fun model(function: DataModel.() -> Unit): Charts {
    model.function()

    area = Area(viewport.x.toDouble(), viewport.y.toDouble(), viewport.width.toDouble(), viewport.height.toDouble())
    settings.duration.from = min(model.chart.start, model.usage.start)
    settings.duration.to = max(model.chart.end, model.usage.end)

    val size = MaxSize(progress, settings)
    zoom.adjustDynamic(size.width, area.width)

    progress.clip = Rectangle2D.Double(0.0,
                                       0.0,
                                       max(zoom.toPixels(size.width), area.width),
                                       size.height)
    usage.clip = Rectangle2D.Double(0.0,
                                    size.height,
                                    progress.clip.width,
                                    max(progress.height * 3, area.height - progress.clip.height - axis.height))
    axis.clip = Rectangle2D.Double(0.0,
                                   progress.clip.height + usage.clip.height,
                                   progress.clip.width,
                                   axis.height)
    return this
  }
}

data class DataModel(private val charts: Charts) {
  internal val chart: ChartModel = ChartModel()
  internal lateinit var usage: UsageModel
  fun progress(init: ChartModel.() -> Unit) {
    chart.init()
  }

  fun usage(type: ChartUsage, init: UsageModel.() -> Unit) {
    charts.usage = type
    usage = type.state
    usage.init()
  }
}

class ChartModel {
  internal var model: MutableMap<EventKey, List<Modules.Event>> = mutableMapOf()
  internal var filter: Predicate<EventKey> = Predicate<EventKey> { _ -> true }
  internal var threads: Int = 0
  internal var start: Long = Long.MAX_VALUE
  internal var end: Long = Long.MIN_VALUE

  fun data(data: MutableMap<EventKey, List<Modules.Event>>) {
    model = data

    data.values.flatten().forEach {
      threads = max(threads, it.threadNumber)
      start = min(start, it.target.time)
      end = max(end, it.target.time)
    }
  }
}

class UsageModel {
  internal var model: MutableSet<StatisticData> = mutableSetOf()
  internal var type: CpuMemoryStatisticsType = MEMORY

  internal var start: Long = Long.MAX_VALUE
  internal var end: Long = Long.MIN_VALUE

  internal var maximum: Long = 0

  fun data(data: MutableSet<StatisticData>) {
    model = data

    data.forEach {
      start = min(start, it.time)
      end = max(end, it.time)
      maximum = max(maximum, it.data)
    }
  }
}

interface ChartComponent {
  fun background(g2d: Graphics2D, type: RenderType, settings: ChartSettings)
  fun component(g2d: Graphics2D, type: RenderType, settings: ChartSettings)
}

class ChartProgress(private val zoom: Zoom, internal val state: ChartModel) : ChartComponent {
  private val model: MutableMap<EventKey, List<Modules.Event>> = mutableMapOf()
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
    lateinit var color: (Modules.Event) -> Color
    lateinit var outline: (Modules.Event) -> Color
    lateinit var selected: (Modules.Event) -> Color
  }

  fun background(init: ModuleBackground.() -> Unit) {
    background = ModuleBackground().apply(init)
  }

  class ModuleBackground {
    lateinit var color: (Int) -> Color
  }

  override fun background(g2d: Graphics2D, type: RenderType, settings: ChartSettings) {
    when (type) {
      RenderType.FULL -> {
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
      RenderType.DIFF -> {
        // todo
      }
    }
  }

  override fun component(g2d: Graphics2D, type: RenderType, settings: ChartSettings) {
    settings.mouse.clear()

    g2d.withAntialiasing {
      when (type) {
        RenderType.FULL -> {
          model.putAll(state.model.getAndClean())
          drawChart(model, settings, g2d)
        }
        RenderType.DIFF -> {
          // todo print cached image
          val data = state.model
          drawChart(data, settings, g2d)
          model.putAll(data.getAndClean())
        }
      }
    }
  }

  private fun Graphics2D.drawChart(data: MutableMap<EventKey, List<Modules.Event>>,
                                   settings: ChartSettings,
                                   g2d: Graphics2D) {
    for ((key, events) in data.filter { state.filter.test(it.key) }) {
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

  private fun getRectangle(start: StartEvent, end: FinishEvent?, settings: ChartSettings): Rectangle2D {
    val x0 = zoom.toPixels(start.target.time - settings.duration.from)
    val x1 = zoom.toPixels((end?.target?.time ?: System.nanoTime()) - settings.duration.from)
    return Rectangle2D.Double(x0, (start.threadNumber * height), x1 - x0, height)
  }
}

class ChartUsage(private val zoom: Zoom, private val name: String, internal val state: UsageModel) : ChartComponent {
  private val model: MutableSet<StatisticData> = TreeSet()

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

  override fun background(g2d: Graphics2D, type: RenderType, settings: ChartSettings) {
    when (type) {
      RenderType.FULL -> {
        model.addAll(state.model.getAndClean())
        g2d.withColor(settings.background) {
          fill(this@ChartUsage.clip)
        }
        g2d.withColor(settings.line.color) {
          draw(Line2D.Double(0.0, this@ChartUsage.clip.y, this@ChartUsage.clip.width, this@ChartUsage.clip.y))
        }
      }
      RenderType.DIFF -> {
        // todo
      }
    }
  }

  override fun component(g2d: Graphics2D, type: RenderType, settings: ChartSettings) {

    when (type) {
      RenderType.FULL -> {
        model.addAll(state.model.getAndClean())
        drawUsageChart(model, settings, g2d)
      }
      RenderType.DIFF -> {
        // todo print cached image
        val data = state.model
        if (drawUsageChart(data, settings, g2d)) return
        model.addAll(data.getAndClean())
      }
    }
  }

  private fun drawUsageChart(data: MutableSet<StatisticData>,
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

  private fun path(data: MutableSet<StatisticData>, settings: ChartSettings): Path2D {
    val y0 = clip.y + clip.height

    val path = Path2D.Double()
    path.moveTo(0.0, y0)
    data.forEach { statistic ->
      path.lineTo(zoom.toPixels(statistic.time - settings.duration.from),
                  y0 - (statistic.data.toDouble() / (state.maximum + 1) * clip.height))
    }

    path.lineTo(zoom.toPixels(data.last().time - settings.duration.from), y0)
    path.closePath()

    return path
  }
}

class ChartAxis(private val zoom: Zoom) : ChartComponent {
  var stroke: FloatArray = floatArrayOf(5f, 5f)
  var distance: Int = 250
  var count: Int = 10
  var height: Double = 0.0
  var padding: Int = 2

  internal lateinit var clip: Rectangle2D

  override fun background(g2d: Graphics2D, type: RenderType, settings: ChartSettings) {
    g2d.withColor(settings.background) {
      fill(this@ChartAxis.clip)
    }
    g2d.withColor(settings.line.color) {
      draw(Line2D.Double(0.0, this@ChartAxis.clip.y, this@ChartAxis.clip.width, this@ChartAxis.clip.y))
    }
  }

  override fun component(g2d: Graphics2D, type: RenderType, settings: ChartSettings) {
    g2d.withAntialiasing {
      val size = UIUtil.getFontSize(settings.font.size) + padding

      val from = 0
      val to = this@ChartAxis.clip.width.toInt()

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

class ChartSettings {
  internal lateinit var font: ChartFont
  internal lateinit var mouse: CompilationChartsMouseAdapter
  var background: Color = JBColor.WHITE
  internal val duration: ChartDuration = ChartDuration()

  internal var line: ChartLine = ChartLine()

  fun font(init: ChartFont.() -> Unit) {
    font = ChartFont().apply(init)
  }

  fun line(init: ChartLine.() -> Unit) {
    line = ChartLine().apply(init)
  }

  class ChartFont {
    var size: FontSize = FontSize.NORMAL
    var color: Color = JBColor.DARK_GRAY
  }

  class ChartDuration {
    var from: Long = Long.MAX_VALUE
    var to: Long = 0
  }
}

class ChartLine {
  var color: Color = JBColor.LIGHT_GRAY
  var size: Int = 1
}

internal data class Area(val x: Double, val y: Double, val width: Double, val height: Double)
internal data class MaxSize(val width: Double, val height: Double) {
  constructor(width: Long, height: Double) : this(width.toDouble(), height)
  constructor(progress: ChartProgress, settings: ChartSettings) : this(with(settings.duration) { to - from }, (progress.state.threads + 1) * progress.height)
}

class CompilationChartsMouseAdapter(private val vm: CompilationChartsViewModel, private val component: Component) : MouseAdapter() {
  private val components: MutableList<Index> = mutableListOf()
  private var currentPopup: JPopupMenu? = null

  override fun mouseClicked(e: MouseEvent) {
    val info = search(e.point)?.info ?: return
    val popupMenu = JPopupMenu().apply {
      add(JLabel(CompilationChartsBundle.message("charts.module.info", info["name"], info["duration"]))) // TODO
      show(this@CompilationChartsMouseAdapter.component, e.point.x, e.point.y)
    }
    currentPopup = popupMenu
  }

  override fun mouseExited(e: MouseEvent) {
    val popup = currentPopup ?: return
    if (popup.isShowing && !popup.contains(e.point)) {
      popup.isVisible = false // todo animation
    }
  }

  fun clear() {
    components.clear()
  }

  fun module(rect: Rectangle2D, key: EventKey, info: Map<String, String>) {
    components.add(Index(rect, key, info))
  }

  private fun search(point: Point): Index? {
    components.forEach { index ->
      if (index.x0 <= point.x && index.x1 >= point.x && index.y0 <= point.y && index.y1 >= point.y) return index
    }
    return null
  }

  private data class Index(val x0: Double, val x1: Double,
                           val y0: Double, val y1: Double,
                           val key: EventKey,
                           val info: Map<String, String>) {
    constructor(rect: Rectangle2D, key: EventKey, info: Map<String, String>) : this(rect.x, rect.x + rect.width,
                                                                                    rect.y, rect.y + rect.height,
                                                                                    key, info)
  }
}