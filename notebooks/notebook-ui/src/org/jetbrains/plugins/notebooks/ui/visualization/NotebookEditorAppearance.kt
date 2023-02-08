// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.notebooks.ui.visualization

import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Key
import java.awt.Color


/**
 * Constants and functions that affects only visual representation, like colors, sizes of elements, etc.
 */
interface NotebookEditorAppearance: NotebookEditorAppearanceColors, NotebookEditorAppearanceSizes, NotebookEditorAppearanceFlags {
  companion object {
    val NOTEBOOK_APPEARANCE_KEY = Key.create<NotebookEditorAppearance>(NotebookEditorAppearance::class.java.name)
  }
}


interface NotebookEditorAppearanceSizes {
  val CODE_CELL_LEFT_LINE_PADDING: Int
  val LINE_NUMBERS_MARGIN: Int

  // TODO Do the pixel constants need JBUI.scale?
  val COMMAND_MODE_CELL_LEFT_LINE_WIDTH: Int
  val EDIT_MODE_CELL_LEFT_LINE_WIDTH: Int
  val CODE_AND_CODE_TOP_GRAY_HEIGHT: Int
  val CODE_AND_CODE_BOTTOM_GRAY_HEIGHT: Int
  val INNER_CELL_TOOLBAR_HEIGHT: Int
  val CELL_BORDER_HEIGHT: Int
  val SPACER_HEIGHT: Int
  val EXECUTION_TIME_HEIGHT: Int
  val SPACE_BELOW_CELL_TOOLBAR: Int
  val CELL_TOOLBAR_TOTAL_HEIGHT: Int
  val PROGRESS_STATUS_HEIGHT: Int

  val JUPYTER_CELL_SPACERS_INLAY_PRIORITY: Int
  val JUPYTER_BELOW_OUTPUT_CELL_SPACERS_INLAY_PRIORITY: Int
  val JUPYTER_CELL_TOOLBAR_INLAY_PRIORITY: Int
  val NOTEBOOK_OUTPUT_INLAY_PRIORITY: Int

  val EXTRA_PADDING_EXECUTION_COUNT: Int

  fun getCellLeftLineWidth(): Int
  fun getCellLeftLineHoverWidth(): Int
  fun getLeftBorderWidth(): Int
  val EXTRA_GUTTER_AREA_WIDTH_EXECUTION_COUNT: Int
}


interface NotebookEditorAppearanceColors {
  // TODO Sort everything lexicographically.

  fun getCodeCellBackground(scheme: EditorColorsScheme): Color? = null
  fun getGutterInputExecutionCountForegroundColor(scheme: EditorColorsScheme): Color? = null
  fun getGutterOutputExecutionCountForegroundColor(scheme: EditorColorsScheme): Color? = null
  fun getProgressStatusRunningColor(scheme: EditorColorsScheme): Color = Color.BLUE
  fun getInlayBackgroundColor(scheme: EditorColorsScheme): Color? = null

  fun getSausageButtonAppearanceBackgroundColor(scheme: EditorColorsScheme): Color = Color.WHITE
  fun getSausageButtonAppearanceForegroundColor(scheme: EditorColorsScheme): Color = Color.BLACK

  fun getSausageButtonShortcutColor(scheme: EditorColorsScheme): Color = Color.GRAY
  fun getSausageButtonBorderColor(scheme: EditorColorsScheme): Color = Color.GRAY

  /**
   * Takes lines of the cell and returns a color for the stripe that will be drawn behind the folding markers.
   * Currently only code cells are supported.
   */
  fun getCellStripeColor(editor: EditorImpl, lines: IntRange): Color? = null
  fun getCellStripeHoverColor(editor: EditorImpl, lines: IntRange): Color? = null
}

interface NotebookEditorAppearanceFlags {
  fun shouldShowCellLineNumbers(): Boolean
  fun shouldShowExecutionCounts(): Boolean
}

object DefaultNotebookEditorAppearanceSizes: NotebookEditorAppearanceSizes {
  // TODO it's hardcoded, but it should be equal to distance between a folding line and an editor.
  override val CODE_CELL_LEFT_LINE_PADDING = 5

  // TODO it's hardcoded, but it should be EditorGutterComponentImpl.getLineNumberAreaWidth()
  override val LINE_NUMBERS_MARGIN = 10

  // TODO Do the pixel constants need JBUI.scale?
  override val COMMAND_MODE_CELL_LEFT_LINE_WIDTH = 4
  override val EDIT_MODE_CELL_LEFT_LINE_WIDTH = 2
  override val CODE_AND_CODE_TOP_GRAY_HEIGHT = 6
  override val CODE_AND_CODE_BOTTOM_GRAY_HEIGHT = 6
  override val INNER_CELL_TOOLBAR_HEIGHT = 25
  override val CELL_BORDER_HEIGHT = 20
  override val SPACER_HEIGHT = CELL_BORDER_HEIGHT / 2
  override val EXECUTION_TIME_HEIGHT = SPACER_HEIGHT + 6
  override val SPACE_BELOW_CELL_TOOLBAR = 12
  override val CELL_TOOLBAR_TOTAL_HEIGHT = INNER_CELL_TOOLBAR_HEIGHT + SPACE_BELOW_CELL_TOOLBAR
  override val PROGRESS_STATUS_HEIGHT = 2

  override val JUPYTER_CELL_SPACERS_INLAY_PRIORITY = 10
  override val JUPYTER_BELOW_OUTPUT_CELL_SPACERS_INLAY_PRIORITY = -10
  override val JUPYTER_CELL_TOOLBAR_INLAY_PRIORITY = JUPYTER_CELL_SPACERS_INLAY_PRIORITY + 10
  override val NOTEBOOK_OUTPUT_INLAY_PRIORITY: Int = 5

  override val EXTRA_PADDING_EXECUTION_COUNT = 25
  override val EXTRA_GUTTER_AREA_WIDTH_EXECUTION_COUNT = 40

  override fun getCellLeftLineWidth(): Int = EDIT_MODE_CELL_LEFT_LINE_WIDTH
  override fun getCellLeftLineHoverWidth(): Int = COMMAND_MODE_CELL_LEFT_LINE_WIDTH

  override fun getLeftBorderWidth(): Int =
    Integer.max(COMMAND_MODE_CELL_LEFT_LINE_WIDTH, EDIT_MODE_CELL_LEFT_LINE_WIDTH) + CODE_CELL_LEFT_LINE_PADDING
}