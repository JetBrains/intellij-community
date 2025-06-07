package com.intellij.notebooks.visualization.ui

import java.util.EventListener

interface EditorCellEventListener : EventListener {
  fun onEditorCellEvents(events: List<EditorCellEvent>)

  sealed interface EditorCellEvent {
    val cell: EditorCell
  }

  data class CellCreated(override val cell: EditorCell) : EditorCellEvent
  data class CellRemoved(override val cell: EditorCell, val index: Int) : EditorCellEvent
}