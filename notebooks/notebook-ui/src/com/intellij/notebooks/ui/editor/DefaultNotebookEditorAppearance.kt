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
import com.intellij.openapi.editor.colors.impl.DelegateColorScheme
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.observable.properties.AtomicProperty
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.openapi.observable.util.operation
import com.intellij.openapi.observable.util.transform
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.ui.JBColor
import java.awt.Color
import kotlin.sequences.generateSequence

open class DefaultNotebookEditorAppearance(
  private val editor: Editor
) : NotebookEditorAppearance, NotebookEditorAppearanceSizes by DefaultNotebookEditorAppearanceSizes {

  private val swapCellAndEditorBackgroundColor = AtomicBooleanProperty(
    Registry.`is`("jupyter.editor.swap.cell.and.editor.background", false)
  ).distinct()

  private val colorsScheme: ObservableMutableProperty<EditorColorsScheme> = AtomicProperty(
    editor.colorsScheme
  )
  override val editorBackgroundColor: ObservableProperty<Color> = operation(colorsScheme, swapCellAndEditorBackgroundColor) { colorsScheme, swapCellAndEditorBackgroundColor ->
    val color = if (swapCellAndEditorBackgroundColor) {
      colorsScheme.getColor(NotebookEditorAppearance.CODE_CELL_BACKGROUND)
    }
    else {
      colorsScheme.getColor(NotebookEditorAppearance.EDITOR_BACKGROUND)
    }
    color ?: colorsScheme.firstNonDelegate().defaultBackground
  }.distinct()

  override val caretRowBackgroundColor: ObservableProperty<Color?> = operation(colorsScheme, swapCellAndEditorBackgroundColor) { colorsScheme, swapCellAndEditorBackgroundColor ->
    val color = if (swapCellAndEditorBackgroundColor) {
      null
    }
    else {
      colorsScheme.getColor(NotebookEditorAppearance.CARET_ROW_COLOR)
    }
    color ?: colorsScheme.getColor(EditorColors.CARET_ROW_COLOR)
  }.distinct()

  override val codeCellBackgroundColor: ObservableProperty<Color> = operation(colorsScheme, swapCellAndEditorBackgroundColor) { colorsScheme, swapCellAndEditorBackgroundColor ->
    val color = if (swapCellAndEditorBackgroundColor) {
      colorsScheme.getColor(NotebookEditorAppearance.EDITOR_BACKGROUND)
    }
    else {
      colorsScheme.getColor(NotebookEditorAppearance.CODE_CELL_BACKGROUND)
    }
    color ?: colorsScheme.firstNonDelegate().defaultBackground
  }.distinct()

  override val cellStripeSelectedColor: ObservableProperty<Color> = colorsScheme
    .transform {
      it.getColor(NotebookEditorAppearance.CELL_STRIPE_SELECTED_COLOR_OLD)
      ?: it.getColor(NotebookEditorAppearance.CELL_STRIPE_SELECTED_COLOR)
      ?: JBColor.BLUE
    }
    .distinct()

  override val cellStripeHoveredColor: ObservableProperty<Color> = colorsScheme
    .transform {
      it.getColor(NotebookEditorAppearance.CELL_STRIPE_HOVERED_COLOR_OLD)
      ?: it.getColor(NotebookEditorAppearance.CELL_STRIPE_HOVERED_COLOR)
      ?: JBColor.GRAY
    }
    .distinct()

  override val cellFrameSelectedColor: ObservableProperty<Color> = colorsScheme
    .transform {
      it.getColor(NotebookEditorAppearance.CELL_FRAME_SELECTED_COLOR)
      ?: it.getColor(NotebookEditorAppearance.CELL_STRIPE_SELECTED_COLOR)
      ?: JBColor.BLUE
    }
    .distinct()

  override val cellFrameHoveredColor: ObservableProperty<Color> = colorsScheme
    .transform {
      it.getColor(NotebookEditorAppearance.CELL_FRAME_HOVERED_COLOR)
      ?: it.getColor(NotebookEditorAppearance.CELL_STRIPE_HOVERED_COLOR)
      ?: JBColor.border()
    }
    .distinct()

  override val cellPopupToolbarBorderColor: ObservableProperty<Color> = colorsScheme
    .transform {
      it.getColor(NotebookEditorAppearance.CELL_TOOLBAR_BORDER_COLOR_OLD)
      ?: it.getColor(NotebookEditorAppearance.CELL_TOOLBAR_BORDER_COLOR)
      ?: JBColor.GRAY
    }
    .distinct()

  init {
    service<NotebookEditorAppearanceManager>().addEditorColorsListener(editor.disposable) {
      colorsScheme.set(editor.colorsScheme)
    }
    Registry.get("jupyter.editor.swap.cell.and.editor.background").addListener(
      object : RegistryValueListener {
        override fun afterValueChanged(value: RegistryValue) {
          swapCellAndEditorBackgroundColor.set(value.asBoolean())
        }
      },
      editor.disposable
    )
  }

  override fun getGutterInputExecutionCountForegroundColor(scheme: EditorColorsScheme): Color? =
    scheme.getColor(GUTTER_INPUT_EXECUTION_COUNT)

  override fun getGutterOutputExecutionCountForegroundColor(scheme: EditorColorsScheme): Color? =
    scheme.getColor(GUTTER_OUTPUT_EXECUTION_COUNT)

  override fun getProgressStatusRunningColor(scheme: EditorColorsScheme): Color =
    scheme.getColor(PROGRESS_STATUS_RUNNING_COLOR) ?: super.getProgressStatusRunningColor(scheme)

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

  private fun EditorColorsScheme.firstNonDelegate(): EditorColorsScheme {
    return generateSequence(this) {
      (it as? DelegateColorScheme)?.delegate
    }.last()
  }
}