package org.jetbrains.plugins.notebooks.ui.editor

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import org.jetbrains.plugins.notebooks.ui.visualization.NotebookEditorAppearance
import org.jetbrains.plugins.notebooks.ui.visualization.NotebookEditorAppearanceSizes
import java.awt.Color

object NewUINotebookDiffEditorAppearance: NotebookEditorAppearance,
                                          NotebookEditorAppearanceSizes by NewUINotebookDiffEditorAppearanceSizes
{
  private val CARET_ROW_COLOR_NEW_UI = ColorKey.createColorKey("JUPYTER.CARET_ROW_COLOR_NEW_UI")

  override fun getCodeCellBackground(scheme: EditorColorsScheme): Color? = // Color.orange
    scheme.getColor(NotebookEditorAppearance.CODE_CELL_BACKGROUND_NEW_UI)

  override fun getCaretRowColor(scheme: EditorColorsScheme): Color? = scheme.getColor(CARET_ROW_COLOR_NEW_UI)
  override fun shouldShowCellLineNumbers(): Boolean = false
  override fun shouldShowExecutionCounts(): Boolean = false  // not needed for DIFF -> execution does not reach it
  override fun shouldShowOutExecutionCounts(): Boolean = false
  override fun shouldShowRunButtonInGutter(): Boolean = false
  override fun getCellStripeHoverColor(editor: Editor): Color {
    return editor.colorsScheme.getColor(NotebookEditorAppearance.CELL_UNDER_CURSOR_STRIPE_HOVER_COLOR) ?: JBColor.BLUE
  }
  override fun getCellStripeColor(editor: Editor): Color {
    return editor.colorsScheme.getColor(NotebookEditorAppearance.CELL_STRIPE_COLOR) ?: JBColor.GRAY
  }
}


object NewUINotebookDiffEditorAppearanceSizes: NotebookEditorAppearanceSizes {
  // see comments in org.jetbrains.plugins.notebooks.ui.visualization.DefaultNotebookEditorAppearanceSizes
  override val CODE_CELL_LEFT_LINE_PADDING = 5
  override val LINE_NUMBERS_MARGIN = 10

  override val COMMAND_MODE_CELL_LEFT_LINE_WIDTH = 4
  override val EDIT_MODE_CELL_LEFT_LINE_WIDTH = 2
  override val CODE_AND_CODE_TOP_GRAY_HEIGHT = 60
  override val CODE_AND_CODE_BOTTOM_GRAY_HEIGHT = 60
  override val INNER_CELL_TOOLBAR_HEIGHT = 24
  override val cellBorderHeight = JBUI.scale(20)
  override val distanceBetweenCells: Int = JBUI.scale(16)
  override val aboveFirstCellDelimiterHeight: Int = JBUI.scale(20)
  override val SPACER_HEIGHT = cellBorderHeight / 2
  override val EXECUTION_TIME_HEIGHT = 0  // not used in the jupyter diff viewer
  override val SPACE_BELOW_CELL_TOOLBAR = 10
  override val CELL_TOOLBAR_TOTAL_HEIGHT = INNER_CELL_TOOLBAR_HEIGHT + SPACE_BELOW_CELL_TOOLBAR
  override val PROGRESS_STATUS_HEIGHT = 2

  override val JUPYTER_CELL_SPACERS_INLAY_PRIORITY = 10
  override val JUPYTER_BELOW_OUTPUT_CELL_SPACERS_INLAY_PRIORITY = -10
  override val JUPYTER_CELL_TOOLBAR_INLAY_PRIORITY = JUPYTER_CELL_SPACERS_INLAY_PRIORITY + 10
  override val NOTEBOOK_OUTPUT_INLAY_PRIORITY: Int = 5

  override val EXTRA_PADDING_EXECUTION_COUNT = 0

  override fun getCellLeftLineWidth(editor: Editor): Int = 10
  override fun getCellLeftLineHoverWidth(): Int = 10

  override fun getLeftBorderWidth(): Int =
    Integer.max(COMMAND_MODE_CELL_LEFT_LINE_WIDTH, EDIT_MODE_CELL_LEFT_LINE_WIDTH) + CODE_CELL_LEFT_LINE_PADDING
}
