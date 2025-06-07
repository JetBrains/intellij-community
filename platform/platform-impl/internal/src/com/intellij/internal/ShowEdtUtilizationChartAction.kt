// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.diagnostic.EdtLockLoadMonitorService
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.JBColor
import com.intellij.ui.charts.*
import com.intellij.ui.components.JBPanel
import com.intellij.util.application
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.seconds

@ApiStatus.Internal
class ShowEdtUtilizationChartAction : DumbAwareAction() {

  override fun actionPerformed(e: AnActionEvent) {
    application.service<EdtLockLoadMonitorService>().initialize()
    val toolWindowManager = ToolWindowManager.getInstance(e.project!!)

    val toolWindowName = InternalActionsBundle.message("tab.title.edt.utilization.chart")
    val maybeCreatedToolWindow = toolWindowManager.getToolWindow(toolWindowName)
    if (maybeCreatedToolWindow != null) {
      maybeCreatedToolWindow.show()
      return
    }

    val toolWindow = toolWindowManager.registerToolWindow(toolWindowName) {
      stripeTitle = InternalActionsBundle.messagePointer("tab.title.edt.utilization.chart")
      canCloseContent = false
      icon = AllIcons.Toolwindows.ToolWindowFind
      anchor = ToolWindowAnchor.BOTTOM
    }

    val chart = produceEdtUtilChart()

    val contentManager = toolWindow.contentManager
    val panel = JBPanel<Nothing>().apply {
      layout = java.awt.BorderLayout()
      add(chart.component, java.awt.BorderLayout.CENTER)
    }

    SwingUtilities.invokeLater {
      val content = com.intellij.ui.content.impl.ContentImpl(panel, "", true)
      contentManager.addContent(content)
    }

    toolWindow.show(null)

    val repaintJob = application.service<ChartPainterScope>().scope.launch {
      repaintChart(chart, panel)
    }

    Disposer.register(toolWindow.disposable, {
      repaintJob.cancel()
    })
  }

  private suspend fun repaintChart(chart: XYLineChart<Long, Int>, panel: JBPanel<Nothing>) = withContext(Dispatchers.Default) {
    val service = ApplicationManager.getApplication().serviceAsync<EdtLockLoadMonitorService>()
    var previousChartData = withContext(Dispatchers.EDT) {
      service.export()
    }
    while (true) {
      delay(1.seconds)
      val chartData = withContext(Dispatchers.EDT) {
        service.export()
      }
      val difference = chartData - previousChartData
      val writeLockDurationY = difference.writeLockDurationRatio
      val writeIntentLockDurationY = difference.writeIntentLockDurationRatio + writeLockDurationY
      val readLockDurationY = difference.readLockDurationRatio + writeIntentLockDurationY
      chart["Write Action"].add(Coordinates(chartData.totalDuration.inWholeSeconds, (writeLockDurationY * 100.0).toInt()))
      chart["Write-Intent Read Action"].add(Coordinates(chartData.totalDuration.inWholeSeconds, (writeIntentLockDurationY * 100.0).toInt()))
      chart["Read Action"].add(Coordinates(chartData.totalDuration.inWholeSeconds, (readLockDurationY * 100.0).toInt()))
      chart.ranges.xMin = chartData.totalDuration.inWholeSeconds - 50
      chart.ranges.xMax = chartData.totalDuration.inWholeSeconds
      chart.ranges.xOrigin = chartData.totalDuration.inWholeSeconds
      previousChartData = chartData
      SwingUtilities.invokeLater {
        panel.repaint()
      }
    }
  }

  fun produceEdtUtilChart(): XYLineChart<Long, Int> = lineChart<Long, Int> {
    ranges {
      yMin = 0
      yMax = 100
      xMin = 0
      xMax = 100
    }
    margins {
      right = 40
      top = 10
      bottom = 10
    }
    grid {
      yLines = enumerator(0, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100)
      xOrigin = xMax
      yPainter {
        majorLine = value == 50
        label = "$value%"
        verticalAlignment = SwingConstants.CENTER
        horizontalAlignment = SwingConstants.RIGHT
      }
    }
    overlays = listOf(LabelOverlay())
    datasets {
      dataset {
        label = "Write Action"
        stacked = true
        lineColor = JBColor.RED
        fillColor = JBColor.RED.transparent(0.5)
        modificationFirst = true
      }
      dataset {
        label = "Write-Intent Read Action"
        stacked = true
        lineColor = JBColor.BLUE
        fillColor = JBColor.BLUE.transparent(0.5)
        modificationFirst = true
      }
      dataset {
        label = "Read Action"
        stacked = true
        lineColor = JBColor.GREEN
        fillColor = JBColor.GREEN.transparent(0.5)
        modificationFirst = true
      }
    }
  }

  @Service(Service.Level.APP)
  private class ChartPainterScope(val scope: CoroutineScope)
}