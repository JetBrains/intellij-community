// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.ui.visualization

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.util.Key
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Color


/**
 * Constants and functions that affects only visual representation, like colors, sizes of elements, etc.
 */
interface NotebookEditorAppearance: NotebookEditorAppearanceColors, NotebookEditorAppearanceSizes, NotebookEditorAppearanceFlags {
  companion object {
    val NOTEBOOK_APPEARANCE_KEY: Key<NotebookEditorAppearance?> = Key.create<NotebookEditorAppearance>(NotebookEditorAppearance::class.java.name)
    val CODE_CELL_BACKGROUND: ColorKey = ColorKey.createColorKey("JUPYTER.CODE_CELL_BACKGROUND")
    val EDITOR_BACKGROUND: ColorKey = ColorKey.createColorKey("JUPYTER.EDITOR_BACKGROUND")
    internal val CELL_STRIPE_HOVERED_COLOR_OLD: ColorKey = ColorKey.createColorKey("JUPYTER.CELL_UNDER_CURSOR_STRIPE_HOVER_COLOR")
    internal val CELL_STRIPE_SELECTED_COLOR_OLD: ColorKey = ColorKey.createColorKey("JUPYTER.CELL_UNDER_CARET_COMMAND_MODE_STRIPE_COLOR")
    internal val CELL_TOOLBAR_BORDER_COLOR_OLD: ColorKey = ColorKey.createColorKey("JUPYTER.SAUSAGE_BUTTON_BORDER_COLOR")
    val CARET_ROW_COLOR: ColorKey = ColorKey.createColorKey("JUPYTER.CARET_ROW_COLOR")
    val CELL_STRIPE_HOVERED_COLOR: ColorKey = ColorKey.createColorKey("JUPYTER.CELL_STRIPE_HOVERED_COLOR")
    val CELL_STRIPE_SELECTED_COLOR: ColorKey = ColorKey.createColorKey("JUPYTER.CELL_STRIPE_SELECTED_COLOR")
    val CELL_TOOLBAR_BORDER_COLOR: ColorKey = ColorKey.createColorKey("JUPYTER.CELL_TOOLBAR_BORDER_COLOR")
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

  val cellBorderHeight: Int
  val aboveFirstCellDelimiterHeight: Int
  val distanceBetweenCells: Int

  fun getCellLeftLineWidth(editor: Editor): Int
  fun getCellLeftLineHoverWidth(): Int
  fun getLeftBorderWidth(): Int
}


interface NotebookEditorAppearanceColors {

  val editorBackgroundColor: ObservableProperty<Color>

  val caretRowBackgroundColor: ObservableProperty<Color?>

  val codeCellBackgroundColor: ObservableProperty<Color>

  val cellStripeHoveredColor: ObservableProperty<Color>

  val cellStripeSelectedColor: ObservableProperty<Color>

  val cellPopupToolbarBorderColor: ObservableProperty<Color>

  // TODO Sort everything lexicographically.

  fun getGutterInputExecutionCountForegroundColor(scheme: EditorColorsScheme): Color? = null
  fun getGutterOutputExecutionCountForegroundColor(scheme: EditorColorsScheme): Color? = null
  fun getProgressStatusRunningColor(scheme: EditorColorsScheme): Color = JBColor.BLUE
  fun getInlayBackgroundColor(scheme: EditorColorsScheme): Color? = null
  fun getTextOutputBackground(scheme: EditorColorsScheme): Color = scheme.defaultBackground
}

interface NotebookEditorAppearanceFlags {
  fun shouldShowCellLineNumbers(): Boolean
  fun shouldShowExecutionCounts(): Boolean
  fun shouldShowOutExecutionCounts(): Boolean
  fun shouldShowRunButtonInGutter(): Boolean
}

object DefaultNotebookEditorAppearanceSizes: NotebookEditorAppearanceSizes {
  // TODO it's hardcoded, but it should be equal to distance between a folding line and an editor.
  override val CODE_CELL_LEFT_LINE_PADDING: Int = 5

  // TODO it's hardcoded, but it should be EditorGutterComponentImpl.getLineNumberAreaWidth()
  override val LINE_NUMBERS_MARGIN: Int = 10

  // TODO Do the pixel constants need JBUI.scale?
  override val COMMAND_MODE_CELL_LEFT_LINE_WIDTH: Int = JBUI.scale(4)
  override val EDIT_MODE_CELL_LEFT_LINE_WIDTH: Int = JBUI.scale(2)
  override val CODE_AND_CODE_TOP_GRAY_HEIGHT: Int = JBUI.scale(6)
  override val CODE_AND_CODE_BOTTOM_GRAY_HEIGHT: Int = JBUI.scale(6)
  override val INNER_CELL_TOOLBAR_HEIGHT: Int = JBUI.scale(24)
  override val distanceBetweenCells: Int = JBUI.scale(16)
  override val cellBorderHeight: Int = JBUI.scale(16)
  override val aboveFirstCellDelimiterHeight: Int = JBUI.scale(24)
  override val SPACER_HEIGHT: Int = JBUI.scale(cellBorderHeight / 2)
  override val EXECUTION_TIME_HEIGHT: Int = JBUI.scale(SPACER_HEIGHT + 14)
  override val SPACE_BELOW_CELL_TOOLBAR: Int = JBUI.scale(4)
  override val CELL_TOOLBAR_TOTAL_HEIGHT: Int = JBUI.scale(INNER_CELL_TOOLBAR_HEIGHT + SPACE_BELOW_CELL_TOOLBAR)
  override val PROGRESS_STATUS_HEIGHT: Int = JBUI.scale(2)

  override val JUPYTER_CELL_SPACERS_INLAY_PRIORITY: Int = 10
  override val JUPYTER_BELOW_OUTPUT_CELL_SPACERS_INLAY_PRIORITY: Int = -10
  override val JUPYTER_CELL_TOOLBAR_INLAY_PRIORITY: Int = JUPYTER_CELL_SPACERS_INLAY_PRIORITY + 10
  override val NOTEBOOK_OUTPUT_INLAY_PRIORITY: Int = 5

  override val EXTRA_PADDING_EXECUTION_COUNT: Int = JBUI.scale(20)

  override fun getCellLeftLineWidth(editor: Editor): Int = EDIT_MODE_CELL_LEFT_LINE_WIDTH
  override fun getCellLeftLineHoverWidth(): Int = COMMAND_MODE_CELL_LEFT_LINE_WIDTH

  override fun getLeftBorderWidth(): Int =
    Integer.max(COMMAND_MODE_CELL_LEFT_LINE_WIDTH, EDIT_MODE_CELL_LEFT_LINE_WIDTH) + CODE_CELL_LEFT_LINE_PADDING
}