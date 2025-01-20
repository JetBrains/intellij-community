package com.intellij.notebooks.visualization.ui.cellsDnD

import com.intellij.util.messages.Topic

interface CellDropNotifier {
  fun cellDropped(event: CellDropEvent)
  
  companion object {
    val CELL_DROP_TOPIC: Topic<CellDropNotifier> = Topic.create("Cell drop", CellDropNotifier::class.java)
  }
}