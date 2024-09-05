package com.intellij.notebooks.visualization.ui

import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.util.Disposer
import com.intellij.notebooks.visualization.UpdateContext
import java.awt.Rectangle

abstract class EditorCellViewComponent : Disposable {
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

  override fun dispose() {
    children.forEach { Disposer.dispose(it) }
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

  fun getInlays(): Sequence<Inlay<*>> {
    return doGetInlays() + children.asSequence().flatMap { it.getInlays() }
  }

  open fun doGetInlays(): Sequence<Inlay<*>> {
    return emptySequence()
  }

  open fun addInlayBelow(presentation: InlayPresentation) {
    throw UnsupportedOperationException("Operation is not supported")
  }

  open fun removeInlayBelow(presentation: InlayPresentation) {
    throw UnsupportedOperationException("Operation is not supported")
  }
}