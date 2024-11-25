// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler.charts.ui

import com.intellij.java.compiler.charts.CompilationChartsBundle
import com.intellij.java.compiler.charts.ui.CompilationChartsAction.Position.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities

class CompilationChartsPopup(
  private val project: Project,
  private val component: CompilationChartsDiagramsComponent,
) {
  private var popup: JBPopup? = null
  private var module: ModuleIndex? = null

  fun open(module: ModuleIndex, location: Point) {
    close()

    val name = module.info["name"] ?: return
    val actions = listOf<CompilationChartsAction>(
      OpenDirectoryAction(project, name) { close() },
      OpenProjectStructureAction(project, name) { close() },
      ShowModuleDependenciesAction(project, name, component) { close() },
      ShowMatrixDependenciesAction(project, name, component) { close() },
    )
    this.module = module
    this.popup = JBPopupFactory.getInstance()
      .createComponentPopupBuilder(content(module.info, actions), null)
      .setResizable(false)
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
    val rectangle = content.bounds.apply {
      x -= 10
      y -= 10
      width += 20
      height += 20
    }

    return rectangle.contains(event.point)
  }

  private fun content(info: Map<String, String>, actions: List<CompilationChartsAction>): JComponent = panel {
    row {
      actions.filter { it.isAccessible() && it.position() == LEFT }
        .forEach { it.draw(this) }

      label(info["name"] ?: "")

      actions.filter { it.isAccessible() && it.position() == RIGHT }
        .forEach { it.draw(this) }
    }

    separator()

    row {
      label(CompilationChartsBundle.message("charts.duration", info["duration"]))
    }

    actions.filter { it.isAccessible() && it.position() == LIST }
      .forEach {
        row { it.draw(this) }
      }
  }.apply {
    border = JBUI.Borders.empty(10)
  }
}