package com.intellij.notebooks.ui.editor

import com.intellij.notebooks.ui.visualization.NotebookEditorAppearance
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.impl.EditorColorsSchemeImpl
import com.intellij.openapi.options.Scheme
import java.awt.Color

object NewUINotebookEditorAppearance: NotebookEditorAppearance by DefaultNotebookEditorAppearance {
  private val CARET_ROW_COLOR_NEW_UI = ColorKey.createColorKey("JUPYTER.CARET_ROW_COLOR_NEW_UI")
  override fun getCodeCellBackground(scheme: EditorColorsScheme): Color? =
    scheme.getColor(NotebookEditorAppearance.CODE_CELL_BACKGROUND_NEW_UI)

  override fun getCaretRowColor(scheme: EditorColorsScheme): Color? {
    val isFromIntellij = (scheme as? EditorColorsSchemeImpl)?.isFromIntellij == true

    return if (scheme.name.startsWith(prefix = Scheme.EDITABLE_COPY_PREFIX) || !isFromIntellij) {
      scheme.getColor(EditorColors.CARET_ROW_COLOR)
    }  else {
      scheme.getColor(CARET_ROW_COLOR_NEW_UI)
    }
  }
}