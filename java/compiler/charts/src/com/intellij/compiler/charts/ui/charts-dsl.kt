// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.charts.ui

import com.intellij.compiler.charts.CompilationChartsBundle
import com.intellij.compiler.charts.CompilationChartsViewModel
import com.intellij.compiler.charts.CompilationChartsViewModel.Modules
import com.intellij.compiler.charts.CompilationChartsViewModel.StatisticData
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
import java.util.concurrent.TimeUnit
import javax.swing.JLabel
import javax.swing.JPopupMenu
import javax.swing.JViewport
import kotlin.math.max

fun charts(vm: CompilationChartsViewModel, zoom: Zoom, viewport: JViewport, init: Charts.() -> Unit): Charts {
  return Charts(vm, zoom, viewport).apply(init).also { charts ->
    val size = MaxSize(charts.progress, charts.settings)
    zoom.adjustDynamic(size.width, charts.area.width)

    charts.progress.clip = Rectangle2D.Double(0.0,
                                              0.0,
                                              max(zoom.toPixels(size.width), charts.area.width),
                                              size.height)
    charts.usage.clip = Rectangle2D.Double(0.0,
                                           size.height,
                                           charts.progress.clip.width,
                                           max(charts.progress.height * 3, charts.area.height - charts.progress.clip.height - charts.axis.height))
    charts.axis.clip = Rectangle2D.Double(0.0,
                                          charts.progress.clip.height + charts.usage.clip.height,
                                          charts.progress.clip.width,
                                          charts.axis.height)
  }
}

class Charts(private val vm: CompilationChartsViewModel, private val zoom: Zoom, viewport: JViewport) {
  internal val area: Area = Area(viewport.x.toDouble(), viewport.y.toDouble(), viewport.width.toDouble(), viewport.height.toDouble())
  internal lateinit var progress: ChartProgress
  internal lateinit var usage: ChartUsage
  internal lateinit var axis: ChartAxis
  internal var settings: ChartSettings = ChartSettings()

  fun settings(init: ChartSettings.() -> Unit) {
    settings = ChartSettings().apply(init)
  }

  fun progress(init: ChartProgress.() -> Unit) {
    progress = ChartProgress(zoom).apply(init)
  }

  fun usage(init: ChartUsage.() -> Unit) {
    usage = ChartUsage(zoom).apply(init)
  }

  fun axis(init: ChartAxis.() -> Unit) {
    axis = ChartAxis(zoom).apply(init)
  }

  fun draw(g2d: Graphics2D, init: Charts.() -> Unit) {
    init()
    val components = listOf(progress, usage, axis)
    components.forEach { it.background(g2d, settings) }
    components.forEach { it.component(g2d, settings) }
  }

  fun width(): Double = axis.clip.width
  fun height(): Double = axis.clip.run { y + height }
}

interface ChartComponent {
  fun background(g2d: Graphics2D, settings: ChartSettings)
  fun component(g2d: Graphics2D, settings: ChartSettings)
}

class ChartProgress(private val zoom: Zoom) : ChartComponent {
  lateinit var model: Map<Modules.EventKey, List<Modules.Event>>
  var selected: Modules.EventKey? = null

  var height: Double = 25.5
  var threads: Int = 0
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

  override fun background(g2d: Graphics2D, settings: ChartSettings) {
    g2d.withColor(settings.background) {
      fill(this@ChartProgress.clip)
    }
    for (row in 0 until threads) {
      val cell = Rectangle2D.Double(clip.x, height * row + clip.y, clip.width, height)
      g2d.withColor(background.color(row)) {
        fill(cell)
      }
    }
  }

  override fun component(g2d: Graphics2D, settings: ChartSettings) {
    settings.mouse.clear()

    g2d.withAntialiasing {
      for ((key, events) in model) {
        val start = events.filterIsInstance<Modules.StartEvent>().firstOrNull() ?: continue
        val end = events.filterIsInstance<Modules.FinishEvent>().firstOrNull()
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
        withColor(if(selected == key) block.selected(start) else block.outline(start)) { // module border
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
  }

  private fun getRectangle(start: Modules.StartEvent, end: Modules.FinishEvent?, settings: ChartSettings): Rectangle2D {
    val x0 = zoom.toPixels(start.target.time - settings.duration.from) + block.border
    val x1 = zoom.toPixels((end?.target?.time ?: System.nanoTime()) - settings.duration.from)
    val width = max(x1 - x0 - block.padding, block.padding) - block.border * 2
    return Rectangle2D.Double(x0, (start.threadNumber * height + block.padding + block.border),
                              width, height - block.padding - block.border * 2
    )
  }
}

class ChartUsage(private val zoom: Zoom) : ChartComponent {
  lateinit var model: Collection<StatisticData>
  lateinit var unit: String
  lateinit var color: UsageColor
  var maximum: Long = 0

  internal lateinit var clip: Rectangle2D

  fun color(init: UsageColor.() -> Unit) {
    color = UsageColor().apply(init)
  }

  class UsageColor {
    lateinit var background: JBColor
    lateinit var border: JBColor
  }

  override fun background(g2d: Graphics2D, settings: ChartSettings) {
    g2d.withColor(settings.background) {
      fill(this@ChartUsage.clip)
    }
    g2d.withColor(settings.line.color) {
      draw(Line2D.Double(0.0, this@ChartUsage.clip.y, this@ChartUsage.clip.width, this@ChartUsage.clip.y))
    }
  }

  override fun component(g2d: Graphics2D, settings: ChartSettings) {
    if (model.isEmpty()) return

    val path = path(settings)
    g2d.withStroke(BasicStroke(USAGE_BORDER)) {
      withColor(this@ChartUsage.color.border) {
        draw(path)
      }
      withColor(this@ChartUsage.color.background) {
        fill(path)
      }
    }
  }

  private fun path(settings: ChartSettings): Path2D {
    val y0 = clip.y + clip.height

    val path = Path2D.Double()
    path.moveTo(0.0, y0)
    model.forEach { statistic ->
      path.lineTo(zoom.toPixels(statistic.time - settings.duration.from),
                  y0 - (statistic.data.toDouble() / maximum * clip.height))
    }

    path.lineTo(zoom.toPixels(model.last().time - settings.duration.from), y0)
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

  override fun background(g2d: Graphics2D, settings: ChartSettings) {
    g2d.withColor(settings.background) {
      fill(this@ChartAxis.clip)
    }
    g2d.withColor(settings.line.color) {
      draw(Line2D.Double(0.0, this@ChartAxis.clip.y, this@ChartAxis.clip.width, this@ChartAxis.clip.y))
    }
  }

  override fun component(g2d: Graphics2D, settings: ChartSettings) {
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
  internal lateinit var duration: ChartDuration

  internal var line: ChartLine = ChartLine()

  fun font(init: ChartFont.() -> Unit) {
    font = ChartFont().apply(init)
  }

  fun duration(init: ChartDuration.() -> Unit) {
    duration = ChartDuration().apply(init)
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
  constructor(progress: ChartProgress, settings: ChartSettings) : this(with(settings.duration) { to - from }, (progress.threads + 1) * progress.height)
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

  fun module(rect: Rectangle2D, key: Modules.EventKey, info: Map<String, String>) {
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
                           val key: Modules.EventKey,
                           val info: Map<String, String>) {
    constructor(rect: Rectangle2D, key: Modules.EventKey, info: Map<String, String>) : this(rect.x, rect.x + rect.width,
                                                                     rect.y, rect.y + rect.height,
                                                                                            key, info)
  }
}