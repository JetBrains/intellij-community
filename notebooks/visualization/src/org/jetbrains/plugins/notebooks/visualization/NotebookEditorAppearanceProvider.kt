package org.jetbrains.plugins.notebooks.visualization

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.plugins.notebooks.ui.visualization.NotebookEditorAppearance
import org.jetbrains.plugins.notebooks.ui.visualization.NotebookEditorAppearance.Companion.NOTEBOOK_APPEARANCE_KEY


private const val ID = "org.jetbrains.plugins.notebooks.editor.notebookEditorAppearanceProvider"


interface NotebookEditorAppearanceProvider {
  fun create(editor: Editor): NotebookEditorAppearance?

  companion object {
    private val EP_NAME = ExtensionPointName.create<NotebookEditorAppearanceProvider>(ID)

    fun create(editor: Editor): NotebookEditorAppearance? =
      EP_NAME.extensions.asSequence().mapNotNull { it.create(editor) }.firstOrNull()

    fun install(editor: Editor) {
      NOTEBOOK_APPEARANCE_KEY.set(editor, create(editor)!!)
    }
  }
}
