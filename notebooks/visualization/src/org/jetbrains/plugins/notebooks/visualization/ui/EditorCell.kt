package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLines
import org.jetbrains.plugins.notebooks.visualization.NotebookIntervalPointer

class EditorCell(
  private val editor: EditorEx,
  internal var intervalPointer: NotebookIntervalPointer,
  private val viewFactory: (EditorCell) -> EditorCellView
) : UserDataHolder by UserDataHolderBase() {

  val source: String
    get() {
      val document = editor.document
      val startOffset = document.getLineStartOffset(interval.lines.first + 1)
      val endOffset = document.getLineEndOffset(interval.lines.last)
      return document.getText(TextRange(startOffset, endOffset))
    }
  val type: NotebookCellLines.CellType get() = interval.type

  private var _visible = true
  val visible: Boolean
    get() = _visible

  private var _selected = false

  var selected: Boolean
    get() = _selected
    set(value) {
      if (_selected != value) {
        _selected = value
        updateSelection(value)
      }
    }

  private fun updateSelection(value: Boolean) {
    view?.updateSelection(value)
  }

  val interval get() = intervalPointer.get() ?: error("Invalid interval")

  var view: EditorCellView? = viewFactory(this)

  private var gutterAction: AnAction? = null

  fun hide() {
    _visible = false
    view?.dispose()
    view = null
  }

  fun show() {
    _visible = true
    if (view == null) {
      view = viewFactory(this)
      view?.updateSelection(_selected)
      gutterAction?.let { view?.setGutterAction(it) }
    }
  }

  fun updatePositions() {
    view?.updatePositions()
  }

  fun dispose() {
    view?.dispose()
  }

  fun update() {
    view?.update()
  }

  fun onViewportChange() {
    view?.onViewportChanges()
  }

  fun setGutterAction(action: AnAction) {
    gutterAction = action
    view?.setGutterAction(action)
  }

  inline fun <reified T : Any> getExtension(): T? {
    return view?.getExtension<T>()
  }

}