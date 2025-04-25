// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.visualization.controllers.selfUpdate.common

import com.intellij.notebooks.ui.visualization.markerRenderers.NotebookLineMarkerRenderer
import com.intellij.notebooks.visualization.context.EditorCellDataContext
import com.intellij.notebooks.visualization.controllers.selfUpdate.SelfManagedCellController
import com.intellij.notebooks.visualization.ui.EditorCell
import com.intellij.notebooks.visualization.ui.addComponentInlay
import com.intellij.notebooks.visualization.ui.updateManager
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.util.Disposer
import javax.swing.JComponent

abstract class NotebookCellSelfInlayController(
  val editorCell: EditorCell,
  val component: JComponent,
) : SelfManagedCellController {
  var inlay: Inlay<*>? = null
    private set

  private val editor
    get() = editorCell.editor

  private val inlayOffset: Int
    get() = if (showAbove)
      editorCell.interval.getCellStartOffset(editor)
    else
      editorCell.interval.getCellEndOffset(editor)

  abstract val showAbove: Boolean
  abstract val gutterHighlighterLayer: Int
  abstract val inlayPriority: Int

  private val highlighterController = object : NotebookCellSelfHighlighterController(editorCell) {
    override fun getHighlighterLayer(): Int = gutterHighlighterLayer

    override fun createLineMarkerRender(rangeHighlighter: RangeHighlighterEx): NotebookLineMarkerRenderer? {
      return this@NotebookCellSelfInlayController.createLineMarkerRender(rangeHighlighter)
    }
  }.also { Disposer.register(this, it) }

  abstract fun createLineMarkerRender(createdHighlighter: RangeHighlighterEx): NotebookLineMarkerRenderer?

  override fun selfUpdate() {
    editor.updateManager.update { updater ->
      updater.addInlayOperation {
        editorCell.intervalOrNull ?: return@addInlayOperation
        if (isInlayCorrect()) {
          updateHighlight()
          return@addInlayOperation
        }
        inlay?.let { Disposer.dispose(it) }
        inlay = null
        val newInlay = createInlay().also { Disposer.register(this, it) }
        inlay = newInlay
        updateHighlight()
      }
    }
  }

  open fun updateHighlight() {
    highlighterController.selfUpdate()
  }

  private fun createInlay(): Inlay<*> {
    val offset = inlayOffset
    return editor.addComponentInlay(
      EditorCellDataContext.createContextProvider(editorCell, component),
      isRelatedToPrecedingText = true,
      showAbove = showAbove,
      priority = inlayPriority,
      offset = offset
    )
  }


  internal fun isInlayCorrect(): Boolean {
    return inlay?.isValid == true && inlay?.offset == inlayOffset
  }
}