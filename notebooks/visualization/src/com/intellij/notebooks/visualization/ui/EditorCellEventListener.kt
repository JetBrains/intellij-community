package com.intellij.notebooks.visualization.ui

import java.util.*

interface EditorCellEventListener : EventListener {
  fun onEditorCellEvents(events: List<EditorCellEvent>)

  sealed interface EditorCellEvent
  data class CellCreated(val cell: EditorCell) : EditorCellEvent
  data class CellRemoved(val cell: EditorCell) : EditorCellEvent
  data class CellUpdated(val cell: EditorCell) : EditorCellEvent
}