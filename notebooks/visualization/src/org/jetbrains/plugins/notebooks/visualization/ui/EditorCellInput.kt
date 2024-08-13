package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.impl.EditorImpl
import org.jetbrains.plugins.notebooks.ui.visualization.notebookAppearance
import org.jetbrains.plugins.notebooks.visualization.NotebookCellInlayController
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLines
import org.jetbrains.plugins.notebooks.visualization.UpdateContext
import java.awt.Rectangle

class EditorCellInput(
  private val editor: EditorImpl,
  componentFactory: NotebookCellInlayController.InputFactory,
  private val cell: EditorCell,
) : EditorCellViewComponent() {

  val interval: NotebookCellLines.Interval
    get() = cell.intervalPointer.get() ?: error("Invalid interval")

  private val shouldShowRunButton =
    editor.editorKind == EditorKind.MAIN_EDITOR &&
    editor.notebookAppearance.shouldShowRunButtonInGutter() &&
    cell.type == NotebookCellLines.CellType.CODE

  val runCellButton: EditorCellRunGutterButton? =
    if (shouldShowRunButton) EditorCellRunGutterButton(editor, cell)
    else null

  val component: EditorCellViewComponent = componentFactory.createComponent(editor, cell).also { add(it) }

  val folding = EditorCellFoldingBar(editor, ::getFoldingBounds) { toggleFolding() }

  private var gutterAction: AnAction? = null

  var folded = false
    private set

  private fun getFoldingBounds(): Pair<Int, Int> {
    //For disposed
    cell
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

  private fun toggleFolding() = cell.manager.update { ctx ->
    folded = !folded
    (component as? InputComponent)?.updateFolding(ctx, folded)
  }

  override fun doDispose() {
    folding.dispose()
    component.dispose()
  }

  fun update() {
    updateInput()
    updateGutterIcons()
  }

  private fun updateGutterIcons() {
    (component as? HasGutterIcon)?.updateGutterIcons(gutterAction)
  }

  fun setGutterAction(action: AnAction?) {
    gutterAction = action
    updateGutterIcons()
  }

  override fun calculateBounds(): Rectangle {
    val linesRange = interval.lines
    val startOffset = editor.document.getLineStartOffset(linesRange.first)
    val endOffset = editor.document.getLineEndOffset(linesRange.last)
    val bounds = editor.inlayModel.getBlockElementsInRange(startOffset, endOffset)
      .asSequence()
      .filter { it.properties.priority > editor.notebookAppearance.NOTEBOOK_OUTPUT_INLAY_PRIORITY }
      .mapNotNull { it.bounds }
      .fold(component.calculateBounds()) { b, i ->
        b.union(i)
      }
    return bounds
  }

  fun updateInput() = cell.manager.update { ctx ->
    (component as? InputComponent)?.updateInput(ctx)
  }

  fun switchToEditMode(ctx: UpdateContext) {
    (component as? InputComponent)?.switchToEditMode(ctx)
  }

  fun switchToCommandMode(ctx: UpdateContext) {
    (component as? InputComponent)?.switchToCommandMode(ctx)
  }

  fun requestCaret() {
    (component as? InputComponent)?.requestCaret()
  }
}