package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.TextRange
import org.jetbrains.plugins.notebooks.ui.visualization.notebookAppearance
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLines
import java.awt.Rectangle

class EditorCellInput(
  private val editor: EditorEx,
  private val componentFactory: (EditorCellInput, EditorCellViewComponent?) -> EditorCellViewComponent,
  private val cell: EditorCell,
): EditorCellViewComponent() {

  val interval: NotebookCellLines.Interval
    get() = cell.intervalPointer.get() ?: error("Invalid interval")

  private var foldRegion: FoldRegion? = null

  private val runCellButton: EditorCellRunButton? =
    if (editor.notebookAppearance.shouldShowRunButtonInGutter()) EditorCellRunButton(editor)
    else null

  private val delimiterPanelSize: Int = when (interval.ordinal) {
    0 -> editor.notebookAppearance.aboveFirstCellDelimiterHeight
    else -> editor.notebookAppearance.cellBorderHeight / 2
  }

  private var _component: EditorCellViewComponent = componentFactory(this, null)
    set(value) {
      if (value != field) {
        field.dispose()
        remove(field)
        field = value
        add(value)
      }
    }

  val component: EditorCellViewComponent
    get() = _component

  private val folding: EditorCellFolding = EditorCellFolding(editor) {
    toggleFolding(componentFactory)
  }

  private fun toggleFolding(inputComponentFactory: (EditorCellInput, EditorCellViewComponent) -> EditorCellViewComponent) {
    _component = if (_component is ControllerEditorCellViewComponent) {
      toggleTextFolding()
      TextEditorCellViewComponent(editor, cell)
    }
    else {
      toggleTextFolding()
      inputComponentFactory(this, _component)
    }
  }

  private fun toggleTextFolding() {
    val interval = interval
    val startOffset = editor.document.getLineStartOffset(interval.lines.first + 1)
    val endOffset = editor.document.getLineEndOffset(interval.lines.last)
    val foldingModel = editor.foldingModel
    val currentFoldingRegion = foldRegion
    if (currentFoldingRegion == null) {
      if (cell.type == NotebookCellLines.CellType.MARKDOWN) cell.view?.disableMarkdownRenderingIfEnabled()
      foldingModel.runBatchFoldingOperation {
        val text = editor.document.getText(TextRange(startOffset, endOffset))
        val firstNotEmptyString = text.lines().firstOrNull { it.trim().isNotEmpty() }
        val placeholder = firstNotEmptyString?.ellipsis(30) ?: "\u2026"
        foldRegion = foldingModel.createFoldRegion(startOffset, endOffset, placeholder, null, true)
      }
    }
    else {
      foldingModel.runBatchFoldingOperation {
        foldingModel.removeFoldRegion(currentFoldingRegion)
        foldRegion = null
      }
      if (cell.type == NotebookCellLines.CellType.MARKDOWN) cell.view?.enableMarkdownRenderingIfNeeded()
    }
  }

  private var gutterAction: AnAction? = null

  override fun doDispose() {
    folding.dispose()
    runCellButton?.dispose()
    _component.dispose()
  }

  fun update(force: Boolean = false) {
    val oldComponent = if (force) null else _component
    _component = componentFactory(this, oldComponent)
    updateGutterIcons()
  }

  private fun updateGutterIcons() {
    (_component as? HasGutterIcon)?.updateGutterIcons(gutterAction)
  }

  override fun doLayout() {
    folding.updatePosition(bounds.y + delimiterPanelSize, bounds.height - delimiterPanelSize)
  }

  fun setGutterAction(action: AnAction) {
    gutterAction = action
    updateGutterIcons()
  }

  fun hideFolding() {
    folding.hide()
  }

  fun showFolding() {
    folding.show()
  }

  fun showRunButton() {
    try {
      runCellButton?.showRunButton(interval)
    }
    catch (e: IllegalStateException) {
      return
    }
  }

  fun hideRunButton() {
    runCellButton?.hideRunButton()
  }

  fun updatePresentation(view: EditorCellViewComponent) {
    _component = view
  }

  fun updateSelection(value: Boolean) {
    folding.updateSelection(value)
  }

  override fun calculateBounds(): Rectangle {
    val linesRange = interval.lines
    val startOffset = editor.document.getLineStartOffset(linesRange.first)
    val endOffset = editor.document.getLineEndOffset(linesRange.last)
    val bounds = editor.inlayModel.getBlockElementsInRange(startOffset, endOffset)
      .asSequence()
      .filter { it.properties.priority > editor.notebookAppearance.NOTEBOOK_OUTPUT_INLAY_PRIORITY }
      .mapNotNull { it.bounds }
      .fold(_component.calculateBounds()) { b, i ->
        b.union(i)
      }
    return bounds
  }
}

private fun String.ellipsis(length: Int): String {
  return if (this.length > length) {
    substring(0, length - 1)
  }
         else {
    this
  } + "\u2026"
}