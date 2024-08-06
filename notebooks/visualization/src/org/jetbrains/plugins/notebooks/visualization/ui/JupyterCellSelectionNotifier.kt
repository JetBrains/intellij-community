package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.editor.Editor
import com.intellij.util.messages.Topic
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLines

interface JupyterCellSelectionNotifier {
  // todo: probably it's better to merge with some already existing listener
  companion object {
    val TOPIC = Topic.create("Jupyter Cell Selection Topic", JupyterCellSelectionNotifier::class.java)
  }

  fun cellSelected(interval: NotebookCellLines.Interval, editor: Editor)
}
