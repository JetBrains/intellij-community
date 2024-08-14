// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler.charts.ui

import com.intellij.java.compiler.charts.CompilationChartsViewModel
import com.intellij.java.compiler.charts.CompilationChartsViewModel.CpuMemoryStatisticsType.CPU
import com.intellij.java.compiler.charts.CompilationChartsViewModel.CpuMemoryStatisticsType.MEMORY
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jetbrains.rd.util.reactive.IViewableList
import javax.swing.JViewport
import javax.swing.ScrollPaneConstants

class CompilationChartsView(project: Project, private val vm: CompilationChartsViewModel) : BorderLayoutPanel() {
  init {
    val zoom = Zoom()

    val scroll = object : JBScrollPane() {
      override fun createViewport(): JViewport = CompilationChartsViewport(zoom)
    }.apply {
      horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_ALWAYS
      verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
      border = JBUI.Borders.empty()
      viewport.scrollMode = JViewport.SIMPLE_SCROLL_MODE
      name = "compilation-charts-scroll-pane"
    }
    val rightAdhesionScrollBarListener = RightAdhesionScrollBarListener(scroll.viewport)
    scroll.horizontalScrollBar.addAdjustmentListener(rightAdhesionScrollBarListener)
    val diagrams = CompilationChartsDiagramsComponent(vm, zoom, scroll.viewport).apply {
      addMouseWheelListener(rightAdhesionScrollBarListener)
      name = "compilation-charts-diagrams-component"
      isFocusable = true
    }

    scroll.setViewportView(diagrams)

    val panel = ActionPanel(project, vm, diagrams)
    addToTop(panel)
    addToCenter(scroll)

    vm.modules.get().advise(vm.lifetime) { module ->
      module.newValueOpt?.let { diagrams.modules.data[module.key] = it }
      ?: diagrams.modules.data.remove(module.key)

      diagrams.statistic.time(vm.modules.start)
      diagrams.statistic.time(vm.modules.end)
      diagrams.statistic.thread(vm.modules.threadCount)

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
      diagrams.smartDraw()
    }

    vm.cpuMemory.advise(vm.lifetime) { filter ->
      diagrams.cpuMemory = filter
      diagrams.smartDraw()
    }

    vm.scrollToEndEvent.advise(vm.lifetime) { _ ->
      rightAdhesionScrollBarListener.scrollToEnd()
    }
  }
}

data class Statistic(var start: Long, var end: Long, var maxMemory: Long, var threadCount: Int, var maxCpu: Long = 100) {
  constructor() : this(Long.MAX_VALUE, 0, 0, 0, 0)

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

  fun thread(count: Int) {
    if (threadCount < count) threadCount = count
  }
}