// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.charts.ui

import com.intellij.compiler.charts.CompilationChartsViewModel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import com.jetbrains.rd.util.reactive.IViewableList
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JViewport
import javax.swing.ScrollPaneConstants

class CompilationChartsView(private val vm: CompilationChartsViewModel) : BorderLayoutPanel() {
  init {
    val init = Initialization()
    val diagrams = CompilationChartsDiagramsComponent(vm, init.init())
    val scroll = JBScrollPane(diagrams).apply {
      horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
      verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
      border = JBUI.Borders.empty()
      viewport.scrollMode = JViewport.SIMPLE_SCROLL_MODE
      name = "compilation-charts-scroll-pane"
      init.set(this)
    }

    val actionPanel = ActionPanel(vm)
    addToTop(actionPanel)
    addToCenter(scroll)

    vm.modules.get().advise(vm.lifetime) { module ->
      module.newValueOpt?.let { data ->
        diagrams.modules.data[module.key] = data
      } ?: diagrams.modules.data.remove(module.key)

      diagrams.statistic.time(vm.modules.start)
      diagrams.statistic.time(vm.modules.end)
      diagrams.statistic.thread(vm.modules.threadCount)
    }

    vm.statistics.cpu.advise(vm.lifetime) { statistics ->
      if (statistics !is IViewableList.Event.Add) return@advise
      diagrams.cpu.add(statistics.newValue)
      diagrams.statistic.time(statistics.newValueOpt?.time)
      diagrams.statistic.cpu(statistics.newValueOpt?.data)

      if (vm.cpuMemory.value == CompilationChartsViewModel.CpuMemoryStatisticsType.CPU) {
        scroll.revalidate()
        scroll.repaint()
      }
    }

    vm.statistics.memoryUsed.advise(vm.lifetime) { statistics ->
      if (statistics !is IViewableList.Event.Add) return@advise
      diagrams.memory.add(statistics.newValue)
      diagrams.statistic.memory(statistics.newValueOpt?.data)
      diagrams.statistic.time(statistics.newValueOpt?.time)

      if (vm.cpuMemory.value == CompilationChartsViewModel.CpuMemoryStatisticsType.MEMORY) {
        scroll.revalidate()
        scroll.repaint()
      }
    }

    vm.filter.advise(vm.lifetime) { filter ->
      diagrams.modules.filter = filter

      scroll.revalidate()
      scroll.repaint()
    }

    vm.cpuMemory.advise(vm.lifetime) { filter ->
      diagrams.cpuMemory = filter

      scroll.revalidate()
      scroll.repaint()
    }
  }

  private class Initialization {
    val scroll: AtomicReference<JBScrollPane> = AtomicReference<JBScrollPane>()
    fun init(): () -> JViewport = { scroll.get().viewport }
    fun set(scroll: JBScrollPane) = this.scroll.set(scroll)
  }
}

data class Statistic(var start: Long, var end: Long, var maxMemory: Long, var threadCount: Int, var maxCpu: Long) {
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