// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runToolbar

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.IdeFrame
import java.awt.Component
import java.awt.Dimension
import java.awt.Point
import javax.swing.SwingUtilities

class RunWidgetResizeController private constructor(val project: Project) : DraggablePane.DragListener {
  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<RunWidgetResizeController>()

    fun fromParentIdeFrame(component: Component): RunWidgetResizeController? {
      val ideFrame = SwingUtilities.getWindowAncestor(component) as? IdeFrame ?: return null
      val project = ideFrame.project ?: return null
      return getInstance(project)
    }
  }

  private var startWidth: Int? = null

  override fun dragStarted(locationOnScreen: Point) {
    startWidth = RunToolbarSettings.getInstance(project).getRunConfigWidth()
  }

  override fun dragged(locationOnScreen: Point, offset: Dimension) {
    startWidth?.let {
      val width = it - offset.width
      RunToolbarSettings.getInstance(project).setRunConfigWidth(width)
    }
  }

  override fun dragStopped(locationOnScreen: Point, offset: Dimension) {
    dragged(locationOnScreen, offset)
    startWidth = null
  }
}