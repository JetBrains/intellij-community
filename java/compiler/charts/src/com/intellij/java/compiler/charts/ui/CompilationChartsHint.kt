// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.compiler.charts.ui

import com.intellij.codeInsight.hint.HintManager
import com.intellij.java.compiler.charts.CompilationChartsBundle
import com.intellij.java.compiler.charts.ui.CompilationChartsAction.Position.*
import com.intellij.openapi.project.Project
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.JComponent
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class CompilationChartsHint(
  private val project: Project,
  private val component: CompilationChartsDiagramsComponent,
) {
  private var module: ModuleIndex? = null
  private var active: Boolean = false
  private var hint: JComponent? = null

  fun module(): ModuleIndex? = module
  fun isActive(): Boolean = active

  fun open(module: ModuleIndex, event: MouseEvent) {
    val actions = listOf<CompilationChartsAction>(
      OpenDirectoryAction(project, module.key) { close() },
      OpenProjectStructureAction(project, module.key.name) { close() },
      ShowModuleDependenciesAction(project, module.key.name, component) { close() },
      ShowMatrixDependenciesAction(project, module.key.name, component) { close() },
    )

    hint = content(module, actions).also { hint ->
      val y = max((module.y0.roundToInt() - hint.height), 0)
      val x = min(module.x1.roundToInt(), component.x + component.width) - max(module.x0.roundToInt(), 0)
      HintManager.getInstance().showHint(hint, RelativePoint(component, event.point), HintManager.HIDE_BY_ESCAPE or
        HintManager.UPDATE_BY_SCROLLING or
        HintManager.HIDE_BY_OTHER_HINT or
        HintManager.HIDE_BY_MOUSEOVER, -1) {
        active = false
      }
    }
    active = true
    this.module = module
  }

  fun isInside(point: Point): Boolean {
    val hint = this.hint ?: return false
    val border = 10
    return (point.x >= 0 - border) && (point.x < hint.width + border) &&
           (point.y >= 0 - border) && (point.y < hint.height + border)
  }

  fun close() {
    HintManager.getInstance().hideAllHints()
    module = null
    hint = null
    active = false
  }

  private fun content(module: ModuleIndex, actions: List<CompilationChartsAction>): JComponent = panel {
    row {
      actions.filter { it.isAccessible() && it.position() == LEFT }
        .forEach { it.draw(this) }

      label(module.key.name)

      actions.filter { it.isAccessible() && it.position() == RIGHT }
        .forEach { it.draw(this) }
    }

    separator()

    row {
      label(CompilationChartsBundle.message("charts.duration", module.info["duration"]))
    }

    actions.filter { it.isAccessible() && it.position() == LIST }
      .forEach {
        row { it.draw(this) }
      }
  }.apply {
    border = JBUI.Borders.empty(10)
  }
}