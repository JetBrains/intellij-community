package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.visualization.NotebookCellInlayManager

class EditorCellSelectionModel(manager: NotebookCellInlayManager) {

  private val _selection = mutableSetOf<EditorCell>()

  val selection: List<EditorCell>
    get() = _selection.sortedBy { it.interval.ordinal }

  init {
    manager.addCellEventsListener(object : EditorCellEventListener {
      override fun onEditorCellEvents(events: List<EditorCellEventListener.EditorCellEvent>) {
        for (event in events) {
          if (event is EditorCellEventListener.CellRemoved) removeCell(event.cell)
        }
      }
    }, manager)
  }

  private fun removeCell(selectedCell: EditorCell) {
    _selection.remove(selectedCell)
    selectedCell.selected = false
  }

  fun replaceSelection(cells: Collection<EditorCell>) {
    val selectionSet = cells.toSet()
    val toRemove = _selection - selectionSet
    val toAdd = selectionSet - _selection
    toRemove.forEach {
      it.selected = false
    }
    toAdd.forEach {
      it.selected = true
    }
    _selection.removeAll(toRemove)
    _selection.addAll(toAdd)
  }

}
