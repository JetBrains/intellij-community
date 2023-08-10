// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runToolbar

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import java.awt.Dimension
import java.awt.Point

@Service(Service.Level.PROJECT)
internal class RunWidgetResizeController private constructor(project: Project) : DraggablePane.DragListener {
  companion object {
    fun getInstance(project: Project): RunWidgetResizeController = project.service()
  }

  private val widthHelper = RunWidgetWidthHelper.getInstance(project)
  private var startWidth: Int? = null

  override fun dragStarted(locationOnScreen: Point) {
    startWidth = widthHelper.runConfig
  }

  override fun dragged(locationOnScreen: Point, offset: Dimension) {
    startWidth?.let {
      widthHelper.runConfig = it - offset.width
    }
  }

  override fun dragStopped(locationOnScreen: Point, offset: Dimension) {
    dragged(locationOnScreen, offset)
    startWidth = null
  }
}