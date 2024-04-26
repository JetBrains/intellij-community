package org.jetbrains.plugins.notebooks.visualization.ui

import java.awt.Dimension
import java.awt.Point
import java.util.EventListener

interface EditorCellViewComponentListener : EventListener {

  fun componentBoundaryChanged(location: Point, size: Dimension) {}

}