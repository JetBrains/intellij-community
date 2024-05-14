// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.charts.ui

import com.intellij.compiler.charts.CompilationChartsViewModel
import com.intellij.compiler.charts.CompilationChartsViewModel.CpuMemoryStatisticsType
import com.intellij.compiler.charts.CompilationChartsViewModel.CpuMemoryStatisticsType.CPU
import com.intellij.compiler.charts.CompilationChartsViewModel.CpuMemoryStatisticsType.MEMORY
import com.intellij.compiler.charts.ui.RenderType.FULL
import com.intellij.ide.ui.UISettings.Companion.setupAntialiasing
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.table.JBTable
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.util.concurrent.ConcurrentSkipListSet
import javax.swing.JViewport
import kotlin.math.exp


class CompilationChartsDiagramsComponent(private val vm: CompilationChartsViewModel,
                                         private val zoom: Zoom,
                                         private val viewport: JViewport) : JBPanelWithEmptyText() {
  companion object {
    val ROW_HEIGHT = JBTable().rowHeight * 1.5
  }

  val modules: CompilationChartsViewModel.ViewModules = CompilationChartsViewModel.ViewModules()
  val stats: Map<CpuMemoryStatisticsType, MutableSet<CompilationChartsViewModel.StatisticData>> = mapOf(MEMORY to ConcurrentSkipListSet(), CPU to ConcurrentSkipListSet())
  var cpu: MutableSet<CompilationChartsViewModel.StatisticData> = ConcurrentSkipListSet()
  var memory: MutableSet<CompilationChartsViewModel.StatisticData> = ConcurrentSkipListSet()
  val statistic: Statistic = Statistic()
  var cpuMemory = MEMORY
  private val mouseAdapter: CompilationChartsMouseAdapter

  private val charts: Charts
  private val usages: Map<CpuMemoryStatisticsType, ChartUsage> = mapOf(
    MEMORY to ChartUsage(zoom, "memory", UsageModel()).apply {
      unit = "MB"
      color {
        background = COLOR_MEMORY
        border = COLOR_MEMORY_BORDER
      }
    },
    CPU to ChartUsage(zoom, "cpu", UsageModel()).apply {
      unit = "%"
      color {
        background = COLOR_CPU
        border = COLOR_CPU_BORDER
      }
    })

  init {
    addMouseWheelListener { e ->
      if (e.isControlDown) {
        zoom.adjustUser(viewport, e.x, exp(e.preciseWheelRotation * -0.05))
        this@CompilationChartsDiagramsComponent.repaint()
      }
      else {
        e.component.parent.dispatchEvent(e)
      }
    }

    mouseAdapter = CompilationChartsMouseAdapter(vm, this)
    addMouseListener(mouseAdapter)

    charts = charts(vm, zoom, viewport) {
      progress {
        height = ROW_HEIGHT

        block {
          border = MODULE_BLOCK_BORDER
          padding = MODULE_BLOCK_PADDING
          color = { m -> if (m.target.isTest) COLOR_TEST_BLOCK else COLOR_PRODUCTION_BLOCK }
          outline = { m -> if (m.target.isTest) COLOR_TEST_BORDER else COLOR_PRODUCTION_BORDER }
          selected = { m -> if (m.target.isTest) COLOR_TEST_BORDER_SELECTED else COLOR_PRODUCTION_BORDER_SELECTED }
        }
        background {
          color = { row -> if (row % 2 == 0) COLOR_BACKGROUND_EVEN else COLOR_BACKGROUND_ODD }
        }
      }
      usage = usages[MEMORY]!!
      axis {
        stroke = floatArrayOf(5f, 5f)
        distance = AXIS_DISTANCE_PX
        count = AXIS_MARKERS_COUNT
        height = ROW_HEIGHT
        padding = AXIS_TEXT_PADDING
      }
      settings {
        font {
          size = FONT_SIZE
          color = COLOR_TEXT
        }
        background = COLOR_BACKGROUND
        line {
          color = COLOR_LINE
        }
        mouse = mouseAdapter
      }
    }
  }

  override fun paintComponent(g2d: Graphics) {
    if (g2d !is Graphics2D) return
    charts.model {
      progress {
        data(modules.data.getAndClean())
        filter = modules.filter
      }
      usage(usages[cpuMemory]!!) {
        data(stats[cpuMemory]!!.getAndClean())
      }
    }.draw(g2d, FULL) {
      setupAntialiasing(g2d) // ??
      val size = Dimension(width().toInt(), height().toInt())
      if (size != this@CompilationChartsDiagramsComponent.preferredSize) {
        this@CompilationChartsDiagramsComponent.preferredSize = size
        this@CompilationChartsDiagramsComponent.revalidate()
      }
    }
  }
}
