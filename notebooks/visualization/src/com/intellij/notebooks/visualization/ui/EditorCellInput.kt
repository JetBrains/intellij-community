package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.notebooks.visualization.EditorCellInputFactory
import com.intellij.notebooks.visualization.ui.cellsDnD.EditorCellDragAssistant
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import java.awt.Rectangle

class EditorCellInput(val cell: EditorCell) : EditorCellViewComponent() {
  private val editor = cell.editor

  val component: EditorCellViewComponent = EditorCellInputFactory.create(cell).also { add(it) }

  private val dragAssistant = when (Registry.`is`("jupyter.editor.dnd.cells")) {
    true -> EditorCellDragAssistant(editor, this, ::fold, ::unfold).also { Disposer.register(this, it) }
    false -> null
  }

  val folding: EditorCellFoldingBar = EditorCellFoldingBar(editor, dragAssistant, ::getFoldingBounds) { toggleFolding() }
    .also {
      Disposer.register(this, it)
    }

  var folded: Boolean = false
    private set

  private fun getFoldingBounds(): Pair<Int, Int> {
    //For disposed
    if (cell.intervalPointer.get() == null) {
      return Pair(0, 0)
    }

    val delimiterPanelSize = if (cell.interval.ordinal == 0) {
      editor.notebookAppearance.aboveFirstCellDelimiterHeight
    }
    else {
      editor.notebookAppearance.cellBorderHeight
    }

    val bounds = calculateBounds()
    return bounds.y + delimiterPanelSize to bounds.height - delimiterPanelSize
  }

  private fun toggleFolding() = editor.updateManager.update { ctx ->
    folded = !folded
    (component as? InputComponent)?.updateFolding(ctx, folded)
  }

  private fun fold() = editor.updateManager.update { ctx ->
    folded = true
    (component as? InputComponent)?.updateFolding(ctx, true)
  }

  private fun unfold() = editor.updateManager.update { ctx ->
    folded = false
    (component as? InputComponent)?.updateFolding(ctx, false)
  }

  fun getBlockElementsInRange(): List<Inlay<*>> {
    val linesRange = cell.interval.lines
    val startOffset = editor.document.getLineStartOffset(linesRange.first)
    val endOffset = editor.document.getLineEndOffset(linesRange.last)
    return editor.inlayModel.getBlockElementsInRange(startOffset, endOffset)
  }

  override fun calculateBounds(): Rectangle {
    return getBlockElementsInRange()
      .asSequence()
      .filter { it.properties.priority > editor.notebookAppearance.cellOutputToolbarInlayPriority }
      .mapNotNull { it.bounds }
      .fold(component.calculateBounds()) { b, i ->
        b.union(i)
      }
  }

  fun updateInput(): Unit? = editor.updateManager.update { ctx ->
    (component as? InputComponent)?.updateInput(ctx)
  }

  fun requestCaret() {
    (component as? InputComponent)?.requestCaret()
  }
}