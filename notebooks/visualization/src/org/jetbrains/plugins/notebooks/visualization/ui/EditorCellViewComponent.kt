package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.actionSystem.AnAction
import java.awt.Dimension
import java.awt.Point

interface EditorCellViewComponent {
  val size: Dimension
  val location: Point

  fun updateGutterIcons(gutterAction: AnAction?)
  fun dispose()
  fun onViewportChange()
  fun addViewComponentListener(listener: EditorCellViewComponentListener)
  fun updatePositions()
}