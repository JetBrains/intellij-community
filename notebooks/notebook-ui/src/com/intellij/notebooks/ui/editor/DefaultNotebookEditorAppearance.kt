package com.intellij.notebooks.ui.editor

import com.intellij.notebooks.ui.editor.actions.command.mode.NotebookEditorMode
import com.intellij.notebooks.ui.editor.actions.command.mode.currentMode
import com.intellij.notebooks.ui.observables.distinct
import com.intellij.notebooks.ui.visualization.DefaultNotebookEditorAppearanceSizes
import com.intellij.notebooks.ui.visualization.NotebookEditorAppearance
import com.intellij.notebooks.ui.visualization.NotebookEditorAppearanceSizes
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.*
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.util.transform
import com.intellij.ui.JBColor
import java.awt.Color

open class DefaultNotebookEditorAppearance(private val editor: Editor) : NotebookEditorAppearance,
                                                                         NotebookEditorAppearanceSizes by DefaultNotebookEditorAppearanceSizes {

  private val colorsScheme: ObservableMutableProperty<EditorColorsScheme> = AtomicProperty<EditorColorsScheme>(
    editor.colorsScheme
  )
  override val editorBackgroundColor: ObservableProperty<Color> = colorsScheme
    .transform { it.getColor(NotebookEditorAppearance.EDITOR_BACKGROUND) ?: it.defaultBackground }
    .distinct()

  override val caretRowBackgroundColor: ObservableProperty<Color> = colorsScheme
    .transform { it.getColor(NotebookEditorAppearance.EDITOR_CARET_ROW_BACKGROUND) ?: codeCellBackgroundColor.get().brighter() }
    .distinct()

  override val codeCellBackgroundColor: ObservableProperty<Color> = colorsScheme
    .transform { it.getColor(NotebookEditorAppearance.CODE_CELL_BACKGROUND) ?: it.defaultBackground.brighter() }
    .distinct()

  override val cellStripeSelectedColor: ObservableProperty<Color> = colorsScheme
    .transform { it.getColor(NotebookEditorAppearance.CELL_STRIPE_SELECTED_COLOR) ?: JBColor.BLUE }
    .distinct()

  override val cellStripeHoveredColor: ObservableProperty<Color> = colorsScheme
    .transform { it.getColor(NotebookEditorAppearance.CELL_STRIPE_HOVERED_COLOR) ?: JBColor.GRAY }
    .distinct()

  init {
    service<NotebookEditorAppearanceManager>().addEditorColorsListener(editor.disposable) {
      colorsScheme.set(editor.colorsScheme)
    }
  }

  override fun getGutterInputExecutionCountForegroundColor(scheme: EditorColorsScheme): Color? =
    scheme.getColor(GUTTER_INPUT_EXECUTION_COUNT)

  override fun getGutterOutputExecutionCountForegroundColor(scheme: EditorColorsScheme): Color? =
    scheme.getColor(GUTTER_OUTPUT_EXECUTION_COUNT)

  override fun getProgressStatusRunningColor(scheme: EditorColorsScheme): Color =
    scheme.getColor(PROGRESS_STATUS_RUNNING_COLOR) ?: super.getProgressStatusRunningColor(scheme)

  override fun getCellLeftLineWidth(editor: Editor): Int =
    when (editor.currentMode) {
      NotebookEditorMode.EDIT -> EDIT_MODE_CELL_LEFT_LINE_WIDTH
      NotebookEditorMode.COMMAND -> COMMAND_MODE_CELL_LEFT_LINE_WIDTH
    }

  override fun getCellLeftLineHoverWidth(): Int =
    COMMAND_MODE_CELL_LEFT_LINE_WIDTH

  override fun shouldShowCellLineNumbers(): Boolean = true

  override fun shouldShowExecutionCounts(): Boolean = true

  override fun shouldShowOutExecutionCounts(): Boolean = false

  override fun shouldShowRunButtonInGutter(): Boolean = true

  companion object {
    val GUTTER_INPUT_EXECUTION_COUNT: ColorKey = ColorKey.createColorKey("JUPYTER.GUTTER_INPUT_EXECUTION_COUNT")

    val GUTTER_OUTPUT_EXECUTION_COUNT: ColorKey = ColorKey.createColorKey("JUPYTER.GUTTER_OUTPUT_EXECUTION_COUNT")

    val PROGRESS_STATUS_RUNNING_COLOR: ColorKey = ColorKey.createColorKey("JUPYTER.PROGRESS_STATUS_RUNNING_COLOR")
  }

  private val Editor.disposable: Disposable
    get() = when (this) {
      is EditorImpl -> disposable
      else -> error("Unsupported editor type: ${this::class}")
    }
}