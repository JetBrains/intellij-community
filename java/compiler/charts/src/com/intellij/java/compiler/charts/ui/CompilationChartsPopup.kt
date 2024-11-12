// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler.charts.ui

import com.intellij.java.compiler.charts.CompilationChartsBundle
import com.intellij.java.compiler.charts.ui.CompilationChartsAction.Position.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.*

class CompilationChartsPopup(
  private val project: Project,
  private val component: CompilationChartsDiagramsComponent,
) {
  private var popup: JBPopup? = null
  private var module: ModuleIndex? = null

  fun open(module: ModuleIndex, location: Point) {
    close()

    val name = module.info["name"] ?: return
    val actions = listOf<CompilationChartsAction>(OpenDirectoryAction(project, name, { close() }),
                                                  OpenProjectStructureAction(project, name, { close() }),
                                                  ShowModuleDependenciesAction(project, name, component, { close() }),
                                                  ShowMatrixDependenciesAction(project, name, component, { close() }),)
    this.module = module
    this.popup = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(content(module.info, actions), null)
      .setResizable(true)
      .setMovable(true)
      .setFocusable(false)
      .setRequestFocus(false)
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

  private fun content(info: Map<String, String>, actions: List<CompilationChartsAction>): JComponent {
    val panel = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      alignmentY = JPanel.TOP_ALIGNMENT
      alignmentX = JPanel.LEFT_ALIGNMENT
      border = JBUI.Borders.empty(10)
    }

    val head = JPanel(BorderLayout()).apply {
      maximumSize = Dimension(Int.MAX_VALUE, 30)
    }

    val center = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
      border = JBUI.Borders.emptyTop(5)
    }

    val footer = JPanel(FlowLayout(FlowLayout.LEFT)).apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      border = JBUI.Borders.emptyTop(5)
      alignmentX = JPanel.LEFT_ALIGNMENT
    }

    val title = JLabel(info["name"]).apply {
      toolTipText = info["name"]
    }

    val duration = JLabel(CompilationChartsBundle.message("charts.duration", info["duration"])).apply {
      toolTipText = info["duration"]
    }

    val left = JPanel().apply {
      actions.filter { action -> action.isAccessible() }
        .filter { action -> action.position() == LEFT }
        .forEach { action -> add(action.label()) }
    }

    val right = JPanel().apply {
      actions.filter { action -> action.isAccessible() }
        .filter { action -> action.position() == RIGHT }
        .forEach { action -> add(action.label()) }
    }

    val list = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      alignmentX = JPanel.LEFT_ALIGNMENT
      border = JBUI.Borders.emptyTop(10)
    }.apply {
      actions.filter { action -> action.isAccessible() }
        .filter { action -> action.position() == LIST }
        .forEach { action -> add(action.label().apply {alignmentX = JPanel.LEFT_ALIGNMENT}) }
    }

    return panel.apply {
      add(head.apply {
        add(left, BorderLayout.WEST)
        add(title, BorderLayout.CENTER)
        add(right, BorderLayout.EAST)
      })
      add(JSeparator(SwingConstants.HORIZONTAL).apply {
        border = JBUI.Borders.empty(0, 10)
        maximumSize = Dimension(Int.MAX_VALUE, 1)
        foreground = UIUtil.getTooltipSeparatorColor()
      })
      add(center.apply {
        add(duration, BorderLayout.WEST)
      })
      add(footer.apply {
        add(list)
      })
    }
  }
}