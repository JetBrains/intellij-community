// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler.charts.ui

import com.intellij.java.compiler.charts.CompilationChartsViewModel
import com.intellij.java.compiler.charts.CompilationChartsViewModel.Modules.EventKey
import com.intellij.java.compiler.charts.CompilationChartsViewModel.StatisticData
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil.FontSize
import java.awt.Color
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseMotionListener
import java.awt.geom.Rectangle2D
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

fun charts(vm: CompilationChartsViewModel, zoom: Zoom, cleanCache: () -> Unit, init: Charts.() -> Unit): Charts {
  return Charts(vm, zoom, cleanCache).apply(init)
}

class Charts(private val vm: CompilationChartsViewModel, private val zoom: Zoom, cleanCache: () -> Unit) {
  private val model: DataModel = DataModel(this)
  internal val progress: ChartProgress = ChartProgress(zoom, model.chart, cleanCache)
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

  fun draw(g2d: ChartGraphics, init: Charts.(Double, Double) -> Unit) {
    init(max(zoom.toPixels(MaxSize(progress, settings).width), width().toDouble()),
         axis.bracket.run { y + height })

    val components = listOf(progress, usage, axis)
    components.forEach { it.background(g2d, settings) }
    components.forEach { it.component(g2d, settings) }
  }

  fun update(init: Charts.() -> Unit) {
    init()
  }

  fun model(function: DataModel.() -> Unit): Charts {
    model.function()
    settings.duration.from = min(model.chart.start, model.usage.start)
    settings.duration.to = max(model.chart.end, model.usage.end)
    return this
  }

  fun width(): Int {
    return listOf(progress, usage, axis).minOfOrNull { it.width(settings) } ?: 0
  }

  fun height(): Double = listOf(progress, usage, axis).sumOf { it.height() }

  fun clips(area: Rectangle2D.Double) {
    val size = MaxSize(progress, settings)
    progress.bracket = Rectangle2D.Double(area.x,
                                          0.0,
                                          area.width,
                                          size.height)
    usage.bracket = Rectangle2D.Double(area.x,
                                       size.height,
                                       progress.bracket.width,
                                       max(progress.height * 3, area.height - progress.bracket.height - axis.height))
    axis.bracket = Rectangle2D.Double(area.x,
                                      progress.bracket.height + usage.bracket.height,
                                      progress.bracket.width,
                                      axis.height)
  }
}

class ChartSettings {
  internal lateinit var font: ChartFont
  internal lateinit var mouse: CompilationChartsModuleInfo
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

  class ChartLine {
    var color: Color = JBColor.LIGHT_GRAY
    var size: Int = 1
  }
}

internal data class MaxSize(val width: Double, val height: Double) {
  constructor(width: Long, height: Double) : this(width.toDouble(), height)
  constructor(progress: ChartProgress, settings: ChartSettings) : this(with(settings.duration) { to - from }, (progress.rows()) * progress.height)
}

class CompilationChartsModuleInfo(
  private val vm: CompilationChartsViewModel,
  private val component: CompilationChartsDiagramsComponent,
) : MouseAdapter() {
  private val components = mutableSetOf<ModuleIndex>()
  private var currentPopup = CompilationChartsPopup(component)

  override fun mouseClicked(e: MouseEvent) {
    component.setFocus()
    val index = search(e.point) ?: return
    currentPopup.open(index, e.locationOnScreen)
  }

  override fun mouseMoved(e: MouseEvent) {
    if (!currentPopup.contains(e)) {
      currentPopup.close()
    }
  }

  fun clear() = components.clear()

  fun module(rect: Rectangle2D, key: EventKey, info: Map<String, String>) {
    components.add(ModuleIndex(rect, key, info))
  }

  private fun search(point: Point): ModuleIndex? = components.firstOrNull { it.contains(point) }
}

class CompilationChartsUsageInfo(val component: CompilationChartsDiagramsComponent, val charts: Charts, val zoom: Zoom) : MouseMotionListener {
  var statistic: StatisticData? = null
  override fun mouseDragged(e: MouseEvent) {
  }

  override fun mouseMoved(e: MouseEvent) {
    val point = e.point
    if (point.y >= charts.usage.bracket.y &&
        point.y <= charts.usage.bracket.y + charts.usage.bracket.height) {
      statistic = search(point)
      if (statistic != null) {
        component.smartDraw(false, false)
      }
    }
    else {
      if (statistic != null) {
        statistic = null
        component.smartDraw(false, false)
      }
    }
  }

  fun draw(g2d: ChartGraphics) {
    statistic?.let { stat ->
      charts.usage.drawPoint(g2d, stat, charts.settings)
    }
  }

  private fun search(point: Point): StatisticData? {
    if (charts.usage.model.isEmpty()) return null
    var statistic = charts.usage.model.first()
    var currentDistance = abs(zoom.toPixels(statistic.time - charts.settings.duration.from) - point.x)
    var lastDistance = currentDistance
    charts.usage.model.forEach { stat ->
      val x = zoom.toPixels(stat.time - charts.settings.duration.from)
      if (abs(point.x - x) < currentDistance) {
        statistic = stat
        lastDistance = currentDistance
        currentDistance = abs(point.x - x)
      }
      else if (lastDistance < currentDistance) {
        return statistic
      }
    }
    return statistic
  }
}

data class ModuleIndex(
  val x0: Double, val x1: Double,
  val y0: Double, val y1: Double,
  val key: EventKey,
  val info: Map<String, String>,
) {
  constructor(rect: Rectangle2D, key: EventKey, info: Map<String, String>) : this(
    rect.x, rect.x + rect.width,
    rect.y, rect.y + rect.height,
    key, info
  )

  fun contains(point: Point): Boolean = x0 <= point.x &&
                                        x1 >= point.x &&
                                        y0 <= point.y &&
                                        y1 >= point.y
}