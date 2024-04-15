package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.TextRange
import com.intellij.util.EventDispatcher
import org.jetbrains.plugins.notebooks.visualization.NotebookIntervalPointer
import java.awt.Dimension
import java.awt.Point

class EditorCellInput(
  private val editor: EditorEx,
  private val componentFactory: (EditorCellViewComponent?) -> EditorCellViewComponent,
  private val intervalPointer: NotebookIntervalPointer
) {

  private val cellEventListeners = EventDispatcher.create(EditorCellViewComponentListener::class.java)

  val location: Point
    get() = _component.location
  val size: Dimension
    get() = _component.size

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
        cellEventListeners.multicaster.componentBoundaryChanged(location, size)
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
      TextEditorCellViewComponent(editor, intervalPointer)
    }
    else {
      toggleTextFolding()
      inputComponentFactory(_component)
    }
  }

  private fun toggleTextFolding() {
    val interval = intervalPointer.get() ?: error("Invalid interval")
    val startOffset = editor.document.getLineStartOffset(interval.lines.first)
    val endOffset = editor.document.getLineEndOffset(interval.lines.last)
    val foldingModel = editor.foldingModel
    val foldRegion = foldingModel.getFoldRegion(startOffset, endOffset)
    if (foldRegion == null) {
      foldingModel.runBatchFoldingOperation {
        val text = editor.document.getText(TextRange(startOffset, endOffset))
        val placeholder = text.lines().drop(1).firstOrNull { it.trim().isNotEmpty() }?.ellipsis(30) ?: "..."
        foldingModel.createFoldRegion(startOffset, endOffset, placeholder, null, true)
      }
    }
    else {
      foldingModel.runBatchFoldingOperation {
        foldingModel.removeFoldRegion(foldRegion)
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
    substring(0, length - 1) + "\u2026"
  }
  else {
    this
  }
}