// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler.charts.ui

import com.intellij.java.compiler.charts.CompilationChartsViewModel
import com.intellij.java.compiler.charts.CompilationChartsViewModel.CpuMemoryStatisticsType.CPU
import com.intellij.java.compiler.charts.CompilationChartsViewModel.CpuMemoryStatisticsType.MEMORY
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jetbrains.rd.util.reactive.IViewableList
import javax.swing.JViewport
import javax.swing.ScrollPaneConstants

class CompilationChartsView(project: Project, private val vm: CompilationChartsViewModel) : BorderLayoutPanel(), UiDataProvider {
  private val diagrams: CompilationChartsDiagramsComponent
  private val rightAdhesionScrollBarListener: RightAdhesionScrollBarListener

  init {
    val zoom = Zoom()
    val scrollType = AutoScrollingType()

    val scroll = object : JBScrollPane() {
      override fun createViewport(): JViewport = CompilationChartsViewport(scrollType)
    }.apply {
      horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS
      verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
      border = JBUI.Borders.empty()
      viewport.scrollMode = JViewport.SIMPLE_SCROLL_MODE
      name = "compilation-charts-scroll-pane"
    }
    rightAdhesionScrollBarListener = RightAdhesionScrollBarListener(scroll.viewport, zoom, scrollType)
    scroll.horizontalScrollBar.addAdjustmentListener(rightAdhesionScrollBarListener)
    diagrams = CompilationChartsDiagramsComponent(vm, zoom, scroll.viewport).apply {
      addMouseWheelListener(rightAdhesionScrollBarListener)
      name = "compilation-charts-diagrams-component"
      isFocusable = true
    }

    scroll.setViewportView(diagrams)

    val panel = ActionPanel(project, vm, scroll.viewport)
    panel.border = JBUI.Borders.customLineBottom(JBColor.border())
    addToTop(panel)
    addToCenter(scroll)

    vm.modules.get().advise(vm.lifetime) { module ->
      module.newValueOpt?.let { diagrams.modules.data[module.key] = it }
      ?: diagrams.modules.data.remove(module.key)

      diagrams.statistic.time(vm.modules.start)
      diagrams.statistic.time(vm.modules.end)

      panel.updateLabel(vm.modules.get().keys, vm.filter.value)
    }

    vm.statistics.cpu.advise(vm.lifetime) { statistics ->
      if (statistics !is IViewableList.Event.Add) return@advise
      diagrams.stats[CPU]!!.add(statistics.newValue)

      diagrams.statistic.cpu(statistics.newValueOpt?.data)
      diagrams.statistic.time(statistics.newValueOpt?.time)
    }

    vm.statistics.memoryUsed.advise(vm.lifetime) { statistics ->
      if (statistics !is IViewableList.Event.Add) return@advise
      diagrams.stats[MEMORY]!!.add(statistics.newValue)

      diagrams.statistic.memory(statistics.newValueOpt?.data)
      diagrams.statistic.maxMemory = vm.statistics.maxMemory
      diagrams.statistic.time(statistics.newValueOpt?.time)
    }

    vm.filter.advise(vm.lifetime) { filter ->
      diagrams.modules.filter = filter
      diagrams.smartDraw(true, false)
    }

    vm.cpuMemory.advise(vm.lifetime) { filter ->
      diagrams.cpuMemory = filter
      diagrams.smartDraw(true, false)
    }
  }

  override fun uiDataSnapshot(sink: DataSink) {
    sink[COMPILATION_CHARTS_VIEW_KEY] = this
  }

  internal fun scrollToEnd() {
    rightAdhesionScrollBarListener.scrollToEnd()
  }

  internal fun zoom(zoomType: ZoomEvent) {
    when (zoomType) {
      ZoomEvent.IN -> rightAdhesionScrollBarListener.increase()
      ZoomEvent.OUT -> rightAdhesionScrollBarListener.decrease()
      ZoomEvent.RESET -> rightAdhesionScrollBarListener.reset()
    }
    diagrams.smartDraw(true, false)
  }
}

enum class ZoomEvent {
  IN,
  OUT,
  RESET
}


internal val COMPILATION_CHARTS_VIEW_KEY = DataKey.create<CompilationChartsView>("CompilationChartsView")

data class Statistic(var start: Long, var end: Long, var maxMemory: Long, var maxCpu: Long = 100) {
  constructor() : this(Long.MAX_VALUE, 0, 0, 0)

  fun time(time: Long?) {
    if (time == null) return
    if (start > time) start = time
    if (end < time) end = time
  }

  fun memory(memory: Long?) {
    if (memory == null) return
    if (maxMemory < memory) maxMemory = memory
  }

  fun cpu(cpu: Long?) {
    if (cpu == null) return
    if (maxCpu < cpu) maxCpu = cpu
  }
}