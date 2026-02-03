package com.intellij.notebooks.visualization

import com.intellij.notebooks.ui.visualization.NotebookEditorAppearance
import com.intellij.notebooks.ui.visualization.NotebookEditorAppearance.Companion.NOTEBOOK_APPEARANCE_KEY
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName

private const val ID = "org.jetbrains.plugins.notebooks.editor.notebookEditorAppearanceProvider"

interface NotebookEditorAppearanceProvider {
  fun create(editor: Editor): NotebookEditorAppearance?

  companion object {
    val EP_NAME: ExtensionPointName<NotebookEditorAppearanceProvider> = ExtensionPointName.create<NotebookEditorAppearanceProvider>(ID)

    fun install(editor: Editor) {
     val appearanceProvider =  EP_NAME.extensionList.asSequence().mapNotNull { it.create(editor) }.firstOrNull() ?: return
      NOTEBOOK_APPEARANCE_KEY.set(editor,appearanceProvider)
    }
  }
}