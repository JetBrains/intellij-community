package com.intellij.notebooks.visualization.ui.cellsDnD

import com.intellij.openapi.editor.Editor
import com.intellij.util.messages.Topic

interface CellDropNotifier {
  fun cellDropped(event: CellDropEvent)

  companion object {
    private val topics = mutableMapOf<Int, Topic<CellDropNotifier>>()

    @JvmStatic
    fun getTopicForEditor(editor: Editor): Topic<CellDropNotifier> {
      val editorHash = editor.hashCode()
      return topics.getOrPut(editorHash) {
        Topic.create("CellDrop:$editorHash", CellDropNotifier::class.java)
      }
    }
  }
}
