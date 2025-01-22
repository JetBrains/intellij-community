package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.ui.visualization.NotebookEditorAppearanceUtils.isOrdinaryNotebookEditor
import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.notebooks.visualization.NotebookCellInlayController
import com.intellij.notebooks.visualization.NotebookCellLines
import com.intellij.notebooks.visualization.ui.cellsDnD.EditorCellDraggableBar
import com.intellij.notebooks.visualization.ui.jupyterToolbars.EditorCellActionsToolbarManager
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import java.awt.Rectangle

class EditorCellInput(
  private val editor: EditorImpl,
  componentFactory: NotebookCellInlayController.InputFactory,
  val cell: EditorCell,
) : EditorCellViewComponent() {

  val interval: NotebookCellLines.Interval
    get() = cell.intervalPointer.get() ?: error("Invalid interval")

  val runCellButton: EditorCellRunGutterButton? =
    if (shouldShowRunButton()) EditorCellRunGutterButton(editor, cell)
    else null

  val component: EditorCellViewComponent = componentFactory.createComponent(editor, cell).also { add(it) }

  val folding: EditorCellFoldingBar = EditorCellFoldingBar(editor, ::getFoldingBounds) { toggleFolding() }
    .also {
      Disposer.register(this, it)
    }

  val draggableBar: EditorCellDraggableBar = EditorCellDraggableBar(editor, this)

  val cellActionsToolbar: EditorCellActionsToolbarManager? =
    if (Registry.`is`("jupyter.per.cell.management.actions.toolbar") && editor.isOrdinaryNotebookEditor()) EditorCellActionsToolbarManager(editor, cell)
    else null

  var folded: Boolean = false
    private set

  private fun shouldShowRunButton(): Boolean {
    return editor.isOrdinaryNotebookEditor() &&
           editor.notebookAppearance.shouldShowRunButtonInGutter() &&
           cell.type == NotebookCellLines.CellType.CODE
  }

  private fun getFoldingBounds(): Pair<Int, Int> {
    //For disposed
    if (cell.intervalPointer.get() == null) {
      return Pair(0, 0)
    }

    val delimiterPanelSize = if (interval.ordinal == 0) {
      editor.notebookAppearance.aboveFirstCellDelimiterHeight
    }
    else {
      editor.notebookAppearance.cellBorderHeight / 2
    }

    val bounds = calculateBounds()
    return bounds.y + delimiterPanelSize to bounds.height - delimiterPanelSize
  }

  private fun toggleFolding() = editor.updateManager.update { ctx ->
    folded = !folded
    (component as? InputComponent)?.updateFolding(ctx, folded)
  }

  override fun dispose() {
    super.dispose()
    Disposer.dispose(folding)
    cellActionsToolbar?.let { Disposer.dispose(it) }
    Disposer.dispose(draggableBar)
  }

  fun update() {
    updateInput()
  }

  fun getBlockElementsInRange(): List<Inlay<*>> {
    val linesRange = interval.lines
    val startOffset = editor.document.getLineStartOffset(linesRange.first)
    val endOffset = editor.document.getLineEndOffset(linesRange.last)
    return editor.inlayModel.getBlockElementsInRange(startOffset, endOffset)
  }

  override fun calculateBounds(): Rectangle {
    return getBlockElementsInRange()
      .asSequence()
      .filter { it.properties.priority > editor.notebookAppearance.NOTEBOOK_OUTPUT_INLAY_PRIORITY }
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