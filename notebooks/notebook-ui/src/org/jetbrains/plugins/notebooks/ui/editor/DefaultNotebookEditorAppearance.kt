package org.jetbrains.plugins.notebooks.ui.editor

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.impl.EditorImpl
import org.jetbrains.plugins.notebooks.ui.editor.actions.command.mode.NotebookEditorMode
import org.jetbrains.plugins.notebooks.ui.editor.actions.command.mode.currentMode
import org.jetbrains.plugins.notebooks.ui.visualization.DefaultNotebookEditorAppearanceSizes
import org.jetbrains.plugins.notebooks.ui.visualization.NotebookEditorAppearance
import org.jetbrains.plugins.notebooks.ui.visualization.NotebookEditorAppearanceSizes
import java.awt.Color


object DefaultNotebookEditorAppearance: NotebookEditorAppearance, NotebookEditorAppearanceSizes by DefaultNotebookEditorAppearanceSizes {
  val CODE_CELL_BACKGROUND = ColorKey.createColorKey("JUPYTER.CODE_CELL_BACKGROUND")
  override fun getCodeCellBackground(scheme: EditorColorsScheme): Color? = scheme.getColor(CODE_CELL_BACKGROUND)

  val GUTTER_INPUT_EXECUTION_COUNT = ColorKey.createColorKey("JUPYTER.GUTTER_INPUT_EXECUTION_COUNT")
  override fun getGutterInputExecutionCountForegroundColor(scheme: EditorColorsScheme): Color? =
    scheme.getColor(GUTTER_INPUT_EXECUTION_COUNT)

  val GUTTER_OUTPUT_EXECUTION_COUNT = ColorKey.createColorKey("JUPYTER.GUTTER_OUTPUT_EXECUTION_COUNT")
  override fun getGutterOutputExecutionCountForegroundColor(scheme: EditorColorsScheme): Color? =
    scheme.getColor(GUTTER_OUTPUT_EXECUTION_COUNT)

  val PROGRESS_STATUS_RUNNING_COLOR = ColorKey.createColorKey("JUPYTER.PROGRESS_STATUS_RUNNING_COLOR")
  override fun getProgressStatusRunningColor(scheme: EditorColorsScheme): Color =
    scheme.getColor(PROGRESS_STATUS_RUNNING_COLOR) ?: super.getProgressStatusRunningColor(scheme)

  val SAUSAGE_BUTTON_APPEARANCE = TextAttributesKey.createTextAttributesKey("JUPYTER.SAUSAGE_BUTTON_APPEARANCE")
  override fun getSausageButtonAppearanceBackgroundColor(scheme: EditorColorsScheme): Color =
    scheme.getAttributes(SAUSAGE_BUTTON_APPEARANCE)?.backgroundColor ?: super.getSausageButtonAppearanceBackgroundColor(scheme)
  override fun getSausageButtonAppearanceForegroundColor(scheme: EditorColorsScheme): Color =
    scheme.getAttributes(SAUSAGE_BUTTON_APPEARANCE)?.foregroundColor ?: super.getSausageButtonAppearanceForegroundColor(scheme)

  val SAUSAGE_BUTTON_SHORTCUT_COLOR = ColorKey.createColorKey("JUPYTER.SAUSAGE_BUTTON_SHORTCUT_COLOR")
  override fun getSausageButtonShortcutColor(scheme: EditorColorsScheme): Color =
    scheme.getColor(SAUSAGE_BUTTON_SHORTCUT_COLOR) ?: super.getSausageButtonShortcutColor(scheme)

  val SAUSAGE_BUTTON_BORDER_COLOR = ColorKey.createColorKey("JUPYTER.SAUSAGE_BUTTON_BORDER_COLOR")
  override fun getSausageButtonBorderColor(scheme: EditorColorsScheme): Color =
    scheme.getColor(SAUSAGE_BUTTON_BORDER_COLOR) ?: super.getSausageButtonBorderColor(scheme)

  val CELL_UNDER_CARET_COMMAND_MODE_STRIPE_COLOR = ColorKey.createColorKey("JUPYTER.CELL_UNDER_CARET_COMMAND_MODE_STRIPE_COLOR")
  val CELL_UNDER_CARET_EDITOR_MODE_STRIPE_COLOR = ColorKey.createColorKey("JUPYTER.CELL_UNDER_CARET_EDITOR_MODE_STRIPE_COLOR")
  private val CELL_UNDER_CURSOR_STRIPE_HOVER_COLOR = ColorKey.createColorKey("JUPYTER.CELL_UNDER_CURSOR_STRIPE_HOVER_COLOR")

  // see org.jetbrains.plugins.notebooks.visualization.CaretBasedCellSelectionModelKt.getSelectionLines
  private fun isCellSelected(editor: Editor, lines: IntRange): Boolean {
    val cellStartOffset = editor.document.getLineStartOffset(lines.first)
    val cellEndOffset = editor.document.getLineEndOffset(lines.last)
    return editor.caretModel.allCarets.any { caret ->
      when {
        caret.offset < caret.selectionStart || caret.offset > caret.selectionEnd -> caret.logicalPosition.line in lines
        cellStartOffset == caret.selectionEnd && caret.offset < cellStartOffset -> false
        else -> cellEndOffset >= caret.selectionStart && caret.selectionEnd >= cellStartOffset
      }
    }
  }
  /**
   * Takes lines of the cell and returns a color for the stripe that will be drawn behind the folding markers.
   * Currently only code cells are supported.
   */
  override fun getCellStripeColor(editor: EditorImpl, lines: IntRange): Color? {
    val isSelected = isCellSelected(editor, lines)
    val color = when {
      isSelected && currentMode() == NotebookEditorMode.COMMAND -> CELL_UNDER_CARET_COMMAND_MODE_STRIPE_COLOR
      isSelected -> CELL_UNDER_CARET_EDITOR_MODE_STRIPE_COLOR
      else -> null
    }
    return color?.let(editor.colorsScheme::getColor)
  }

  override fun getCellStripeHoverColor(editor: EditorImpl, lines: IntRange): Color? {
    val hoveredGutterLine = editor.notebookGutterHoverLine
    if (hoveredGutterLine != null && lines.contains(hoveredGutterLine)) {
      return editor.colorsScheme.getColor(CELL_UNDER_CURSOR_STRIPE_HOVER_COLOR)
    }
    return null
  }

  override fun getCellLeftLineWidth(): Int =
    when (currentMode()) {
      NotebookEditorMode.EDIT -> EDIT_MODE_CELL_LEFT_LINE_WIDTH
      NotebookEditorMode.COMMAND -> COMMAND_MODE_CELL_LEFT_LINE_WIDTH
    }

  override fun getCellLeftLineHoverWidth(): Int =
    COMMAND_MODE_CELL_LEFT_LINE_WIDTH

  override fun shouldShowCellLineNumbers(): Boolean = true

  override fun shouldShowExecutionCounts(): Boolean = true
}