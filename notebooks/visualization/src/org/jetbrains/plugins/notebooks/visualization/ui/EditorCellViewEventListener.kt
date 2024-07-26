package org.jetbrains.plugins.notebooks.visualization.ui

import java.util.*

interface EditorCellViewEventListener : EventListener {
  fun onEditorCellViewEvents(events: List<EditorCellViewEvent>)

  sealed interface EditorCellViewEvent
  data class CellViewCreated(val cell: EditorCellView) : EditorCellViewEvent
  data class CellViewRemoved(val view: EditorCellView) : EditorCellViewEvent
}