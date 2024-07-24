package org.jetbrains.plugins.notebooks.visualization.ui

import org.jetbrains.plugins.notebooks.visualization.UpdateContext
import java.awt.Rectangle

abstract class EditorCellViewComponent {
  protected var parent: EditorCellViewComponent? = null

  private val children = mutableListOf<EditorCellViewComponent>()

  fun add(child: EditorCellViewComponent) {
    children.add(child)
    child.parent = this
  }

  fun remove(child: EditorCellViewComponent) {
    children.remove(child)
    child.parent = null
  }

  fun dispose() {
    children.forEach { it.dispose() }
    doDispose()
  }

  open fun doDispose() = Unit

  fun onViewportChange() {
    children.forEach { it.onViewportChange() }
    doViewportChange()
  }

  open fun doViewportChange() = Unit

  abstract fun calculateBounds(): Rectangle

  open fun updateCellFolding(updateContext: UpdateContext) {
    children.forEach {
      it.updateCellFolding(updateContext)
    }
  }
}