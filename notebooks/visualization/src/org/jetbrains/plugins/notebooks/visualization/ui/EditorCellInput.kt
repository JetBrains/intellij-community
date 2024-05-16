package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.FoldRegion
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.TextRange
import com.intellij.util.EventDispatcher
import org.jetbrains.plugins.notebooks.ui.visualization.notebookAppearance
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLines
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle

class EditorCellInput(
  private val editor: EditorEx,
  private val componentFactory: (EditorCellViewComponent?) -> EditorCellViewComponent,
  private val cell: EditorCell
) {

  private val cellEventListeners = EventDispatcher.create(EditorCellViewComponentListener::class.java)

  val interval: NotebookCellLines.Interval
    get() = cell.intervalPointer.get() ?: error("Invalid interval")

  private var foldRegion: FoldRegion? = null

  val bounds: Rectangle
    get() {
      val linesRange = interval.lines
      val startOffset = editor.document.getLineStartOffset(linesRange.first)
      val endOffset = editor.document.getLineEndOffset(linesRange.last)
      val bounds = editor.inlayModel.getBlockElementsInRange(startOffset, endOffset)
        .asSequence()
        .filter { it.properties.priority > editor.notebookAppearance.NOTEBOOK_OUTPUT_INLAY_PRIORITY }
        .mapNotNull { it.bounds }
        .fold(Rectangle(_component.location, _component.size)) { b, i ->
          b.union(i)
        }
      return bounds
    }

  private var _component: EditorCellViewComponent = componentFactory(null).also { bind(it) }
    set(value) {
      if (value != field) {
        field.dispose()
        field = value
        bind(value)
      }
    }

  private fun bind(value: EditorCellViewComponent) {
    value.addViewComponentListener(object : EditorCellViewComponentListener {
      override fun componentBoundaryChanged(location: Point, size: Dimension) {
        cellEventListeners.multicaster.componentBoundaryChanged(bounds.location, bounds.size)
      }
    })
  }

  val component: EditorCellViewComponent
    get() = _component

  private val folding: EditorCellFolding = EditorCellFolding(editor) {
    toggleFolding(componentFactory)
  }.also {
    cellEventListeners.addListener(object : EditorCellViewComponentListener {
      override fun componentBoundaryChanged(location: Point, size: Dimension) {
        it.updatePosition(location.y, size.height)
      }
    })
  }

  private fun toggleFolding(inputComponentFactory: (EditorCellViewComponent) -> EditorCellViewComponent) {
    _component = if (_component is ControllerEditorCellViewComponent) {
      _component.dispose()
      toggleTextFolding()
      TextEditorCellViewComponent(editor, cell)
    }
    else {
      toggleTextFolding()
      inputComponentFactory(_component)
    }
  }

  private fun toggleTextFolding() {
    val interval = interval
    val startOffset = editor.document.getLineStartOffset(interval.lines.first + 1)
    val endOffset = editor.document.getLineEndOffset(interval.lines.last)
    val foldingModel = editor.foldingModel
    val currentFoldingRegion = foldRegion
    if (currentFoldingRegion == null) {
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
    }
  }

  private var gutterAction: AnAction? = null

  fun dispose() {
    folding.dispose()
    _component.dispose()
  }

  fun update() {
    _component = componentFactory(_component)
    updateGutterIcons()
  }

  private fun updateGutterIcons() {
    _component.updateGutterIcons(gutterAction)
  }

  fun updatePositions() {
    _component.updatePositions()
  }

  fun onViewportChange() {
    _component.onViewportChange()
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

  fun addViewComponentListener(listener: EditorCellViewComponentListener) {
    cellEventListeners.addListener(listener)
  }

  fun updatePresentation(view: EditorCellViewComponent) {
    _component.dispose()
    _component = view
  }

  fun updateSelection(value: Boolean) {
    folding.updateSelection(value)
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