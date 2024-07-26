package org.jetbrains.plugins.notebooks.visualization.ui

import java.awt.Rectangle

abstract class EditorCellViewComponent {

  var bounds: Rectangle = Rectangle(0, 0, 0, 0)
    set(value) {
      if (value != field) {
        invalidate()
      }
      field = value
    }

  private var parent: EditorCellViewComponent? = null

  private val children = mutableListOf<EditorCellViewComponent>()

  private var valid = false

  fun add(child: EditorCellViewComponent) {
    children.add(child)
    child.parent = this
    invalidate()
  }

  fun remove(child: EditorCellViewComponent) {
    children.remove(child)
    child.parent = null
    invalidate()
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

  fun isValid(): Boolean = valid

  fun validate() {
    if (!valid) {
      doLayout()
      children.forEach { it.validate() }
      valid = true
    }
  }

  open fun doLayout() {
    children.forEach {
      it.bounds = it.calculateBounds()
    }
  }

  fun invalidate() {
    if (valid) {
      doInvalidate()
      valid = false
      parent?.invalidate()
    }
  }

  open fun doInvalidate() = Unit
}