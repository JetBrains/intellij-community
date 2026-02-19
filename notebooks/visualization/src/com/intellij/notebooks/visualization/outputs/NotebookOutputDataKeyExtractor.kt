package com.intellij.notebooks.visualization.outputs

import com.intellij.notebooks.visualization.NotebookCellLines
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName

interface NotebookOutputDataKeyExtractor {
  /**
   * Seeks somewhere for some data to be represented below [interval].
   *
   * @return
   *  `null` if the factory can never extract any data from the [interval] of the [editor].
   *  An empty list if the factory managed to extract some information, and it literally means there's nothing to be shown.
   *  A non-empty list if some data can be shown.
   */
  fun extract(editor: Editor, interval: NotebookCellLines.Interval): List<NotebookOutputDataKey>?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<NotebookOutputDataKeyExtractor> =
      ExtensionPointName.create("org.jetbrains.plugins.notebooks.editor.outputs.notebookOutputDataKeyExtractor")
  }
}