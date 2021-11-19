package org.jetbrains.plugins.notebooks.visualization

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.Key


private const val ID = "org.jetbrains.plugins.notebooks.editor.notebookEditorAppearanceProvider"
private val key = Key.create<NotebookEditorAppearance>(NotebookEditorAppearance::class.java.name)


interface NotebookEditorAppearanceProvider {
  fun create(editor: Editor): NotebookEditorAppearance?

  companion object {
    private val EP_NAME = ExtensionPointName.create<NotebookEditorAppearanceProvider>(ID)

    fun create(editor: Editor): NotebookEditorAppearance? =
      EP_NAME.extensions.asSequence().mapNotNull { it.create(editor) }.firstOrNull()

    fun install(editor: Editor) {
      key.set(editor, create(editor)!!)
    }
  }
}


val Editor.notebookAppearance: NotebookEditorAppearance
  get() = key.get(this)!!
