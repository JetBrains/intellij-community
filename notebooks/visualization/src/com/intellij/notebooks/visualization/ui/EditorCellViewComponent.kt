package com.intellij.notebooks.visualization.ui

import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.util.Disposer
import com.intellij.notebooks.visualization.UpdateContext
import java.awt.Rectangle
import java.util.Collections

abstract class EditorCellViewComponent : Disposable {
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

  /* Chile disposable will be automatically disposed. */
  fun remove(child: EditorCellViewComponent) {
    Disposer.dispose(child)
    _children.remove(child)
    child.parent = null
  }

  override fun dispose() = Unit

  fun onViewportChange() {
    _children.forEach { it.onViewportChange() }
    doViewportChange()
  }

  open fun doViewportChange() = Unit

  abstract fun calculateBounds(): Rectangle

  open fun updateCellFolding(updateContext: UpdateContext) {
    _children.forEach {
      it.updateCellFolding(updateContext)
    }
  }

  fun getInlays(): Sequence<Inlay<*>> {
    return doGetInlays() + _children.asSequence().flatMap { it.getInlays() }
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

  /**
   * As there are so many possible document editing operations that can destroy cell inlays by removing document range they attached to,
   * the only option we have to preserve consistency is to check inlays validity
   * and recreate them if needed.
   * This logic is supposed to be as simple as check `isValid` and `offset` attributes of inlays
   * so it should not introduce significant performance degradation.
   */
  fun checkAndRebuildInlays() {
    _children.forEach { it.checkAndRebuildInlays() }
    doCheckAndRebuildInlays()
  }

  open fun doCheckAndRebuildInlays() {}
}