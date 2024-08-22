// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler.charts.ui

import com.intellij.java.compiler.charts.CompilationChartsBundle
import com.intellij.java.compiler.charts.CompilationChartsViewModel
import com.intellij.java.compiler.charts.CompilationChartsViewModel.Modules.EventKey
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil.FontSize
import java.awt.Color
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.Rectangle2D
import javax.swing.JLabel
import javax.swing.JPopupMenu
import kotlin.math.max
import kotlin.math.min

fun charts(vm: CompilationChartsViewModel, zoom: Zoom, init: Charts.() -> Unit): Charts {
  return Charts(vm, zoom).apply(init)
}

class Charts(private val vm: CompilationChartsViewModel, private val zoom: Zoom) {
  private val model: DataModel = DataModel(this)
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

  fun draw(g2d: ChartGraphics, init: Charts.(Double, Double) -> Unit) {
    init(max(zoom.toPixels(MaxSize(progress, settings).width), width().toDouble()),
         axis.clip.run { y + height })

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
    return listOf(progress, usage, axis).maxOfOrNull { it.width(settings) } ?: 0
  }

  fun height(): Double = listOf(progress, usage, axis).sumOf { it.height() }

  fun clips(area: Rectangle2D.Double) {
    val size = MaxSize(progress, settings)
    progress.clip = Rectangle2D.Double(area.x,
                                       0.0,
                                       area.width,
                                       size.height)
    usage.clip = Rectangle2D.Double(area.x,
                                    size.height,
                                    progress.clip.width,
                                    max(progress.height * 3, area.height - progress.clip.height - axis.height))
    axis.clip = Rectangle2D.Double(area.x,
                                   progress.clip.height + usage.clip.height,
                                   progress.clip.width,
                                   axis.height)
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

  class ChartLine {
    var color: Color = JBColor.LIGHT_GRAY
    var size: Int = 1
  }
}

internal data class MaxSize(val width: Double, val height: Double) {
  constructor(width: Long, height: Double) : this(width.toDouble(), height)
  constructor(progress: ChartProgress, settings: ChartSettings) : this(with(settings.duration) { to - from }, (progress.state.threads + 1) * progress.height)
}

class CompilationChartsMouseAdapter(private val vm: CompilationChartsViewModel, private val component: CompilationChartsDiagramsComponent) : MouseAdapter() {
  private val components: MutableList<Index> = mutableListOf()
  private var currentPopup: JPopupMenu? = null

  override fun mouseClicked(e: MouseEvent) {
    component.setFocus()
    search(e.point)?.info?.let { info ->
      currentPopup = JPopupMenu(info["name"]).apply {
        add(JLabel(CompilationChartsBundle.message("charts.module.info", info["name"], info["duration"])))
        show(this@CompilationChartsMouseAdapter.component, e.point.x, e.point.y)
      }
    }
  }

  override fun mouseExited(e: MouseEvent) {
    val popup = currentPopup ?: return
    if (popup.isShowing && !popup.contains(e.point)) {
      popup.isVisible = false
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