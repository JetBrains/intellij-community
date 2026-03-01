package com.intellij.notebooks.ui.editor

import com.intellij.notebooks.ui.editor.actions.command.mode.NotebookEditorMode
import com.intellij.notebooks.ui.editor.actions.command.mode.currentMode
import com.intellij.notebooks.ui.visualization.DefaultNotebookEditorAppearanceSizes
import com.intellij.notebooks.ui.visualization.NotebookEditorAppearance
import com.intellij.notebooks.ui.visualization.NotebookEditorAppearanceSizes
import com.intellij.openapi.editor.Editor
import com.intellij.ui.JBColor
import java.awt.Color

open class DefaultNotebookEditorAppearance(
  private val editor: Editor,
) : NotebookEditorAppearance, NotebookEditorAppearanceSizes by DefaultNotebookEditorAppearanceSizes {

  override fun editorBackgroundColor(): Color {
    return editor.colorsScheme.getColor(NotebookEditorAppearance.EDITOR_BACKGROUND)
           ?: editor.colorsScheme.defaultBackground
  }

  override fun codeCellBackgroundColor(): Color {
    return editor.colorsScheme.getColor(NotebookEditorAppearance.CODE_CELL_BACKGROUND)
           ?: editor.colorsScheme.defaultBackground
  }

  override fun cellStripeSelectedColor(): Color {
    return editor.colorsScheme.getColor(NotebookEditorAppearance.CELL_STRIPE_SELECTED_COLOR)
           ?: editor.colorsScheme.getColor(NotebookEditorAppearance.CELL_STRIPE_SELECTED_COLOR_OLD)
           ?: JBColor.BLUE
  }

  override fun cellStripeHoveredColor(): Color {
    return editor.colorsScheme.getColor(NotebookEditorAppearance.CELL_STRIPE_HOVERED_COLOR)
           ?: editor.colorsScheme.getColor(NotebookEditorAppearance.CELL_STRIPE_HOVERED_COLOR_OLD)
           ?: JBColor.GRAY
  }

  override fun cellFrameSelectedColor(): Color {
    return editor.colorsScheme.getColor(NotebookEditorAppearance.CELL_FRAME_SELECTED_COLOR)
           ?: editor.colorsScheme.getColor(NotebookEditorAppearance.CELL_STRIPE_SELECTED_COLOR)
           ?: JBColor.BLUE
  }

  override fun cellFrameHoveredColor(): Color {
    return editor.colorsScheme.getColor(NotebookEditorAppearance.CELL_FRAME_HOVERED_COLOR)
           ?: editor.colorsScheme.getColor(NotebookEditorAppearance.CELL_STRIPE_HOVERED_COLOR)
           ?: JBColor.border()
  }

  override fun caretRowColor(): Color? {
    return editor.colorsScheme.getColor(NotebookEditorAppearance.CARET_ROW_COLOR)
  }

  override fun getCellLeftLineWidth(editor: Editor): Int =
    when (editor.currentMode) {
      NotebookEditorMode.EDIT -> editModeCellLeftLineWidth
      NotebookEditorMode.COMMAND -> commandModeCellLeftLineWidth
    }

  override fun getCellLeftLineHoverWidth(): Int = commandModeCellLeftLineWidth

  override fun shouldShowCellLineNumbers(): Boolean = true

  override fun shouldShowExecutionCounts(): Boolean = true

  override fun shouldShowOutExecutionCounts(): Boolean = false

  override fun shouldShowRunButtonInGutter(): Boolean = true
}