package org.jetbrains.plugins.notebooks.ui.editor

import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorColorsScheme
import org.jetbrains.plugins.notebooks.ui.visualization.NotebookEditorAppearance
import java.awt.Color

object NewUINotebookEditorAppearance: NotebookEditorAppearance by DefaultNotebookEditorAppearance {
  private val CARET_ROW_COLOR_NEW_UI = ColorKey.createColorKey("JUPYTER.CARET_ROW_COLOR_NEW_UI")
  override fun getCodeCellBackground(scheme: EditorColorsScheme): Color? =
    scheme.getColor(NotebookEditorAppearance.CODE_CELL_BACKGROUND_NEW_UI)

  override fun getCaretRowColor(scheme: EditorColorsScheme): Color? = scheme.getColor(CARET_ROW_COLOR_NEW_UI)
}