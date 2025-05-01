package com.intellij.notebooks.visualization.ui

import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.notebooks.visualization.UpdateContext
import com.intellij.notebooks.visualization.controllers.NotebookCellController
import com.intellij.openapi.util.Disposer
import java.awt.Rectangle
import java.util.*

abstract class EditorCellViewComponent : NotebookCellController {
  protected var parent: EditorCellViewComponent? = null

  private val _children = mutableListOf<EditorCellViewComponent>()

  val children: List<EditorCellViewComponent>
    get() = Collections.unmodifiableList(_children)

  /* Add automatically registers child disposable. */
  fun add(child: EditorCellViewComponent) {
    _children.add(child)
    child.parent = this
    Disposer.register(this, child)
  }

  /* Child will be automatically disposed. */
  fun remove(child: EditorCellViewComponent) {
    Disposer.dispose(child)
    _children.remove(child)
    child.parent = null
  }

  fun onViewportChange() {
    _children.forEach { it.onViewportChange() }
    doViewportChange()
  }

  open fun doViewportChange(): Unit = Unit

  abstract fun calculateBounds(): Rectangle

  open fun updateCellFolding(updateContext: UpdateContext) {
    _children.forEach {
      it.updateCellFolding(updateContext)
    }
  }

  open fun addInlayBelow(presentation: InlayPresentation) {
    throw UnsupportedOperationException("Operation is not supported")
  }

  open fun removeInlayBelow(presentation: InlayPresentation) {
    throw UnsupportedOperationException("Operation is not supported")
  }


  final override fun checkAndRebuildInlays() {
    _children.forEach { it.checkAndRebuildInlays() }
    doCheckAndRebuildInlays()
  }

  open fun doCheckAndRebuildInlays() {}
}