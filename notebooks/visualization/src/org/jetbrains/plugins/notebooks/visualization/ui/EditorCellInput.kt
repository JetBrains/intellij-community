package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.FoldingModelImpl
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.plugins.notebooks.ui.visualization.notebookAppearance
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLines
import java.awt.Rectangle

class EditorCellInput(
  private val editor: EditorImpl,
  private val componentFactory: (EditorCellInput, EditorCellViewComponent?) -> EditorCellViewComponent,
  private val cell: EditorCell,
) : EditorCellViewComponent() {

  val interval: NotebookCellLines.Interval
    get() = cell.intervalPointer.get() ?: error("Invalid interval")

  private val shouldShowRunButton =
    editor.editorKind != EditorKind.DIFF &&
    editor.notebookAppearance.shouldShowRunButtonInGutter() &&
    cell.type == NotebookCellLines.CellType.CODE

  val runCellButton: EditorCellRunGutterButton? =
    if (shouldShowRunButton) EditorCellRunGutterButton(editor, cell)
    else null

  var component: EditorCellViewComponent = componentFactory(this, null).also { add(it) }
    private set(value) {
      if (value != field) {
        field.dispose()
        remove(field)
        field = value
        add(value)
      }
    }

  val folding = EditorCellFoldingBar(editor, ::getFoldingBounds) { toggleFolding(componentFactory) }

  private var gutterAction: AnAction? = null

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

  private fun toggleFolding(inputComponentFactory: (EditorCellInput, EditorCellViewComponent) -> EditorCellViewComponent) {
    component = if (component is ControllerEditorCellViewComponent) {
      toggleTextFolding()
      TextEditorCellViewComponent(editor, cell)
    }
    else {
      toggleTextFolding()
      inputComponentFactory(this, component)
    }
  }

  private fun toggleTextFolding() {
    val interval = interval
    val startOffset = editor.document.getLineStartOffset(interval.lines.first + 1)
    val endOffset = editor.document.getLineEndOffset(interval.lines.last)
    val foldingModel = editor.foldingModel
    val currentFoldingRegion = foldingModel.getFoldRegion(startOffset, endOffset)
    if (currentFoldingRegion == null) {
      if (cell.type == NotebookCellLines.CellType.MARKDOWN) cell.view?.disableMarkdownRenderingIfEnabled()
      foldingModel.runBatchFoldingOperation {
        val text = editor.document.getText(TextRange(startOffset, endOffset))
        val firstNotEmptyString = text.lines().firstOrNull { it.trim().isNotEmpty() }
        val placeholder = StringUtil.shortenTextWithEllipsis(firstNotEmptyString ?: "\u2026", 20, 0)
        foldingModel.createFoldRegion(startOffset, endOffset, placeholder, null, false)?.apply {
          FoldingModelImpl.hideGutterRendererForCollapsedRegion(this)
          isExpanded = false
        }
      }
    }
    else {
      foldingModel.runBatchFoldingOperation {
        if (currentFoldingRegion.isExpanded) {
          currentFoldingRegion.isExpanded = false
        } else {
            foldingModel.removeFoldRegion(currentFoldingRegion)
        }
      }
      if (cell.type == NotebookCellLines.CellType.MARKDOWN) cell.view?.enableMarkdownRenderingIfNeeded()
    }
  }

  override fun doDispose() {
    folding.dispose()
    component.dispose()
  }

  fun update(force: Boolean = false) {
    updateInput(force)
    updateGutterIcons()
  }

  private fun updateGutterIcons() {
    (component as? HasGutterIcon)?.updateGutterIcons(gutterAction)
  }

  fun setGutterAction(action: AnAction) {
    gutterAction = action
    updateGutterIcons()
  }

  fun updatePresentation(view: EditorCellViewComponent) {
    component = view
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


  fun updateInput(force: Boolean = false) {
    val oldComponent = if (force) null else component
    component = componentFactory(this, oldComponent)
  }
}