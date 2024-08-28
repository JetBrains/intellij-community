// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler.charts.ui

import com.intellij.java.compiler.charts.CompilationChartsViewModel
import com.intellij.java.compiler.charts.CompilationChartsViewModel.CpuMemoryStatisticsType
import com.intellij.java.compiler.charts.CompilationChartsViewModel.CpuMemoryStatisticsType.CPU
import com.intellij.java.compiler.charts.CompilationChartsViewModel.CpuMemoryStatisticsType.MEMORY
import com.intellij.java.compiler.charts.ui.CompilationChartsModuleInfo.CompilationChartsUsageInfo
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.table.JBTable
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.TimeUnit
import javax.swing.JButton
import javax.swing.JViewport
import javax.swing.SwingUtilities
import kotlin.math.exp
import kotlin.math.roundToInt

class CompilationChartsDiagramsComponent(
  private val vm: CompilationChartsViewModel,
  private val zoom: Zoom,
  private val viewport: JViewport,
) : JBPanelWithEmptyText(BorderLayout()) {
  companion object {
    val ROW_HEIGHT = JBTable().rowHeight * 1.5
  }

  val modules: CompilationChartsViewModel.ViewModules = CompilationChartsViewModel.ViewModules()
  val stats: Map<CpuMemoryStatisticsType, MutableSet<CompilationChartsViewModel.StatisticData>> = mapOf(MEMORY to ConcurrentSkipListSet(), CPU to ConcurrentSkipListSet())
  var cpu: MutableSet<CompilationChartsViewModel.StatisticData> = ConcurrentSkipListSet()
  var memory: MutableSet<CompilationChartsViewModel.StatisticData> = ConcurrentSkipListSet()
  val statistic: Statistic = Statistic()
  var cpuMemory = MEMORY
  private val usageInfo: CompilationChartsUsageInfo
  private val charts: Charts
  private val images: MutableMap<Int, BufferedImage> = HashMap()
  private val imageRequestCount: MutableMap<Int, Int> = HashMap()
  private var colorScheme: EditorColorsScheme = EditorColorsManager.getInstance().getGlobalScheme()
  private var flush: Boolean = true

  private val focusableEmptyButton = JButton().apply {
    preferredSize = Dimension(0, 0)
    isFocusable = true
    isOpaque = false
    isContentAreaFilled = false
    isBorderPainted = false
  }

  private val usages: Map<CpuMemoryStatisticsType, ChartUsage> = mapOf(
    MEMORY to ChartUsage(zoom, "memory", UsageModel()).apply {
      format = { stat -> "${stat.data / (1024 * 1024)} MB" }
      color {
        background = COLOR_MEMORY
        border = COLOR_MEMORY_BORDER
      }
    },
    CPU to ChartUsage(zoom, "cpu", UsageModel()).apply {
      format = { stat -> "${stat.data} %" }
      color {
        background = COLOR_CPU
        border = COLOR_CPU_BORDER
      }
    })

  init {
    add(focusableEmptyButton, BorderLayout.NORTH)
    addMouseWheelListener { e ->
      if (e.isControlDown) {
        zoom.adjustUser(viewport, e.x, exp(e.preciseWheelRotation * -0.05))
        smartDraw(true, false)
      }
      else {
        e.component.parent.dispatchEvent(e)
      }
    }

    charts = charts(vm, zoom, { cleanCache() }) {
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
        mouse = CompilationChartsModuleInfo(vm, this@CompilationChartsDiagramsComponent)
      }
    }

    addMouseListener(charts.settings.mouse)
    usageInfo = CompilationChartsUsageInfo(this, charts, zoom)
    addMouseMotionListener(usageInfo)


    AppExecutorUtil.createBoundedScheduledExecutorService("Compilation charts component", 1)
      .scheduleWithFixedDelay({ smartDraw() }, 0, REFRESH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
  }

  internal fun cleanCache() {
    images.clear()
    imageRequestCount.clear()
  }

  internal fun smartDraw(clean: Boolean = false, flush: Boolean = true) {
    if (this.flush) this.flush = flush
    if (clean) {
      cleanCache()
      charts.settings.mouse.clear()
    }
    val currentGlobalScheme = EditorColorsManager.getInstance().getGlobalScheme()
    if (colorScheme != currentGlobalScheme) {
      cleanCache()
      colorScheme = currentGlobalScheme
    }
    revalidate()
    repaint()
  }

  override fun paintComponent(g2d: Graphics) {
    if (g2d !is Graphics2D) return
    charts.model {
      progress {
        model = if (flush) modules.data else mutableMapOf()
        filter = modules.filter
        currentTime = if (flush) System.nanoTime() else currentTime
      }
      usage(usages[cpuMemory]!!) {
        model = if (flush) stats[cpuMemory]!! else mutableSetOf()
        maximum = cpuMemory.max(vm.statistics)
      }
    }

    buffered(ChartGraphics(g2d, 0, 0)) { img ->
      charts.draw(img) { width, height ->
        val size = Dimension(width.roundToInt(), height.roundToInt())
        if (size != this@CompilationChartsDiagramsComponent.preferredSize) {
          this@CompilationChartsDiagramsComponent.preferredSize = size
          this@CompilationChartsDiagramsComponent.revalidate()
        }
        img.setupRenderingHints()
      }
      flush = true
    }
    usageInfo.draw(ChartGraphics(g2d, 0, 0))
  }

  internal fun setFocus() {
    SwingUtilities.invokeLater {
      focusableEmptyButton.requestFocusInWindow()
    }
  }

  override fun addNotify() {
    super.addNotify()
    setFocus()
  }

  private fun buffered(
    g2d: ChartGraphics,
    draw: (ChartGraphics) -> Unit,
  ) {
    val start: Int = viewport.viewPosition.x / BUFFERED_IMAGE_WIDTH_PX
    val end: Int = (viewport.viewPosition.x + viewport.width) / BUFFERED_IMAGE_WIDTH_PX + 1

    for (index in start..end) {
      charts.clips(Rectangle2D.Double((index * BUFFERED_IMAGE_WIDTH_PX).toDouble(), viewport.y.toDouble(),
                                      BUFFERED_IMAGE_WIDTH_PX.toDouble(), this.height.toDouble()))

      val area = Rectangle2D.Double((index * BUFFERED_IMAGE_WIDTH_PX).toDouble(), viewport.y.toDouble(),
                                    BUFFERED_IMAGE_WIDTH_PX.toDouble(), charts.height())

      val image = images[index]
      if (image != null && image.height() == area.height) {
        g2d.moveTo(area.x, area.y).drawImage(image, this)
      }
      else {
        if (charts.width() < area.width) {
          draw(g2d)
        }
        else {
          val counter = imageRequestCount.compute(index) { _, v -> (v ?: 0) + 1 } ?: 1
          if (counter < IMAGE_CACHE_ACTIVATION_COUNT) {
            draw(g2d)
          }
          else {
            val img = UIUtil.createImage(this, area.width.roundToInt(), area.height.roundToInt(), BufferedImage.TYPE_INT_ARGB)
            images[index] = img
            val chartGraphics = ChartGraphics(img.createGraphics(), -area.x, -area.y)
            draw(chartGraphics)
            g2d.moveTo(area.x, area.y).drawImage(img, this)
          }
        }
      }
    }
  }
}