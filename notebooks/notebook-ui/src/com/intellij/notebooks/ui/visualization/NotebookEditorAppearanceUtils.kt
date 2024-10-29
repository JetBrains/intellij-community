package com.intellij.notebooks.ui.visualization

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.util.Key

object NotebookEditorAppearanceUtils {
  // todo: carefully merge with NotebookUtil.kt
  val JUPYTER_HISTORY_EDITOR_KEY = Key.create<Boolean>("JUPYTER_HISTORY_EDITOR_KEY")

  fun getJupyterCellSpacing(editor: Editor): Int = editor.getLineHeight()
  fun EditorKind.isDiff(): Boolean = this === EditorKind.DIFF
  fun Editor.isDiffKind(): Boolean = this.editorKind == EditorKind.DIFF

  fun Editor.isOrdinaryNotebookEditor() = when {
    this.editorKind != EditorKind.MAIN_EDITOR -> false
    this.getUserData(JUPYTER_HISTORY_EDITOR_KEY) == true -> false
    else -> true
  }
}