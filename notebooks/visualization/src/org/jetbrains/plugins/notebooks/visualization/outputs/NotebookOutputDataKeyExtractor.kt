package org.jetbrains.plugins.notebooks.visualization.outputs

import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.plugins.notebooks.visualization.NotebookCellLines

/** Merely a marker for data that can be represented via some Swing component. */
interface NotebookOutputDataKey {
  /**
      Get content that can be used for building diff for outputs.
   */
  fun getContentForDiffing(): Any
}

interface NotebookOutputDataKeyExtractor {
  /**
   * Seeks somewhere for some data to be represented below [interval].
   *
   * @return
   *  `null` if the factory can never extract any data from the [interval] of the [editor].
   *  An empty list if the factory managed to extract some information, and it literally means there's nothing to be shown.
   *  A non-empty list if some data can be shown.
   */
  fun extract(editor: EditorImpl, interval: NotebookCellLines.Interval): List<NotebookOutputDataKey>?

  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<NotebookOutputDataKeyExtractor> =
      ExtensionPointName.create("org.jetbrains.plugins.notebooks.editor.outputs.notebookOutputDataKeyExtractor")
  }
}