// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler.charts.ui

import com.intellij.ide.ui.UISettings
import com.intellij.java.compiler.charts.CompilationChartsViewModel
import com.intellij.java.compiler.charts.CompilationChartsViewModel.CpuMemoryStatisticsType
import com.intellij.java.compiler.charts.CompilationChartsViewModel.CpuMemoryStatisticsType.CPU
import com.intellij.java.compiler.charts.CompilationChartsViewModel.CpuMemoryStatisticsType.MEMORY
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBPanelWithEmptyText
import com.intellij.ui.components.Magnificator
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import java.util.concurrent.ConcurrentSkipListSet
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
  val modules: CompilationChartsViewModel.ViewModules = CompilationChartsViewModel.ViewModules()
  val stats: Map<CpuMemoryStatisticsType, MutableSet<CompilationChartsViewModel.StatisticData>> = mapOf(MEMORY to ConcurrentSkipListSet(), CPU to ConcurrentSkipListSet())
  var cpu: MutableSet<CompilationChartsViewModel.StatisticData> = ConcurrentSkipListSet()
  var memory: MutableSet<CompilationChartsViewModel.StatisticData> = ConcurrentSkipListSet()
  val statistic: Statistic = Statistic()
  var cpuMemory = MEMORY
  var offset: Int = 0
  private val usageInfo: CompilationChartsUsageInfo
  private val charts: Charts
  private val images: MutableMap<Int, BufferedImage> = HashMap()
  private val imageRequestCount: MutableMap<Int, Int> = HashMap()
  private val ideSettings: IDESettings = IDESettings()
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
        background = Colors.Memory.BACKGROUND
        border = Colors.Memory.BORDER
      }
    },
    CPU to ChartUsage(zoom, "cpu", UsageModel()).apply {
      format = { stat -> "${stat.data} %" }
      color {
        background = Colors.Cpu.BACKGROUND
        border = Colors.Cpu.BORDER
      }
    })

  init {
    add(focusableEmptyButton, BorderLayout.NORTH)
    addMouseWheelListener { e ->
      if (e.isControlDown) {
        zoom.adjust(viewport, e.x, exp(e.preciseWheelRotation * -0.05))
        smartDraw(true, false)
      }
      else {
        e.component.parent.dispatchEvent(e)
      }
    }

    charts = charts(vm, zoom, { cleanCache() }) {
      progress {
        height = Settings.Block.HEIGHT

        block {
          border = Settings.Block.BORDER
          padding = Settings.Block.PADDING
          color = { m -> Colors.getBlock(m.target.isTest).ENABLED }
          outline = { m -> Colors.getBlock(m.target.isTest).BORDER }
          selected = { m -> Colors.getBlock(m.target.isTest).SELECTED }
        }
        background {
          color = { row -> Colors.Background.getRowColor(row) }
        }
      }
      usage = usages[MEMORY]!!
      axis {
        stroke = floatArrayOf(5f, 5f)
        distance = Settings.Axis.DISTANCE
        count = Settings.Axis.MARKERS_COUNT
        height = Settings.Block.HEIGHT
        padding = Settings.Axis.TEXT_PADDING
      }
      settings {
        font {
          size = Settings.Font.SIZE
          color = Colors.TEXT
        }
        background = Colors.Background.DEFAULT
        line {
          color = Colors.LINE
        }
        mouse = CompilationChartsModuleInfo(vm, this@CompilationChartsDiagramsComponent)
      }
    }

    addMouseListener(charts.settings.mouse)
    usageInfo = CompilationChartsUsageInfo(this, charts, zoom)
    addMouseMotionListener(usageInfo)

    ApplicationManager.getApplication().getMessageBus().connect(vm.disposable)
      .subscribe(EditorColorsManager.TOPIC, EditorColorsListener { scheme -> smartDraw(true, false) })

    putClientProperty(Magnificator.CLIENT_PROPERTY_KEY, object : Magnificator {
      override fun magnify(magnification: Double, at: Point): Point = zoom.adjust(viewport, at.x, magnification)
    })

    AppExecutorUtil.createBoundedScheduledExecutorService("Compilation charts component", 1)
      .scheduleWithFixedDelay({ if (hasNewData()) smartDraw() }, 0, Settings.Refresh.timeout, Settings.Refresh.unit)
      .also { feature -> Disposer.register(vm.disposable) { -> feature.cancel(true) } }
  }

  internal fun cleanCache() {
    images.clear()
    imageRequestCount.clear()
    charts.settings.mouse.clear()
  }

  internal fun smartDraw(clean: Boolean = false, flush: Boolean = true) {
    if (this.flush) this.flush = flush
    if (clean || ideSettings.isChanged()) {
      ideSettings.update()
      cleanCache()
    }
    viewport.revalidate()
    viewport.repaint()
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

    buffered(ChartGraphics(g2d, offset, 0)) { img ->
      charts.draw(img.withRenderingHints()) { width, height -> resizeViewport(width, height) }
    }

    usageInfo.draw(ChartGraphics(g2d, 0, 0))
    flush = true
  }

  private fun resizeViewport(width: Double, height: Double) {
    val size = Dimension(width.roundToInt(), height.roundToInt())
    if (size != preferredSize) {
      preferredSize = size
      revalidate()
    }
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
    val start: Int = viewport.viewPosition.x / Settings.Image.WIDTH
    val end: Int = (viewport.viewPosition.x + viewport.width) / Settings.Image.WIDTH + 1

    for (index in start..end) {
      charts.clips(Rectangle2D.Double((index * Settings.Image.WIDTH).toDouble(), viewport.y.toDouble(),
                                      Settings.Image.WIDTH.toDouble(), viewport.height.toDouble()))

      val area = Rectangle2D.Double((index * Settings.Image.WIDTH).toDouble(), viewport.y.toDouble(),
                                    Settings.Image.WIDTH.toDouble(), charts.height())

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
          if (counter < Settings.Image.CACHE_ACTIVATION_COUNT) {
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

  private fun hasNewData(): Boolean {
    if (!flush) return false
    if (modules.data.isNotEmpty()) return true
    if (stats[cpuMemory]!!.isNotEmpty()) return true
    return false
  }

  private class IDESettings {
    private var scale: Float = scale()
    private var color: EditorColorsScheme = color()

    fun isChanged(): Boolean = scale != scale() || color != color()
    fun update() {
      scale = scale()
      color = color()
    }

    private fun scale() = UISettings.getInstance().ideScale
    private fun color() = EditorColorsManager.getInstance().globalScheme
  }
}