// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler.charts.ui

import com.intellij.java.compiler.charts.CompilationChartsBundle
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.*

class CompilationChartsPopup(
  private val component: CompilationChartsDiagramsComponent,
) {
  private var popup: JBPopup? = null
  private var module: ModuleIndex? = null

  fun open(module: ModuleIndex, location: Point) {
    close()

    this.module = module
    this.popup = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(content(module.info), null) // content
      .setBorderColor(JBUI.CurrentTheme.Tooltip.borderColor())
      .setResizable(true)
      .setMovable(true)
      .setFocusable(true)
      .setRequestFocus(true)
      .setCancelOnClickOutside(true)
      .setCancelOnOtherWindowOpen(false)
      .setCancelOnWindowDeactivation(false)
      .setCancelOnMouseOutCallback { event -> !contains(event) }
      .createPopup().apply {
        size = content.preferredSize
        showInScreenCoordinates(component, location)
      }
  }

  fun close() {
    popup?.cancel()
    popup = null
    module = null
  }

  fun contains(e: MouseEvent): Boolean {
    if (module?.contains(e.point) == true) return true
    val content = popup?.content ?: return false
    val event = SwingUtilities.convertMouseEvent(e.component, e, content)
    return content.contains(event.point)
  }

  private fun content(info: Map<String, String>): JComponent {
    val panel = JPanel(BorderLayout()).apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      alignmentY = JPanel.TOP_ALIGNMENT
      border = JBUI.Borders.empty(10)
      background = UIUtil.getToolTipBackground()
    }

    val title = JLabel(info["name"]).apply {
      foreground = UIUtil.getToolTipForeground()
      toolTipText = info["name"]
    }

    val duration = JLabel(CompilationChartsBundle.message("charts.duration", info["duration"])).apply {
      foreground = UIUtil.getToolTipForeground()
      toolTipText = info["duration"]
    }

    return panel.apply {
      add(title)
      add(JSeparator(SwingConstants.HORIZONTAL).apply {
        border = JBUI.Borders.empty(0, 10)
        maximumSize = Dimension(Int.MAX_VALUE, 1)
      })
      add(duration)
    }
  }
}