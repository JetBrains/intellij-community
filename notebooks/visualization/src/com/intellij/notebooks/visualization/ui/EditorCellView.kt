package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.ui.bind
import com.intellij.notebooks.ui.visualization.NotebookUtil.notebookAppearance
import com.intellij.notebooks.visualization.EditorCellInputFactory
import com.intellij.notebooks.visualization.NotebookCellInlayManager
import com.intellij.notebooks.visualization.NotebookCellLines
import com.intellij.notebooks.visualization.UpdateContext
import com.intellij.notebooks.visualization.controllers.selfUpdate.SelfManagedCellController
import com.intellij.notebooks.visualization.controllers.selfUpdate.SelfManagedControllerFactory
import com.intellij.notebooks.visualization.ui.cellsDnD.DropHighlightable
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.registry.Registry
import java.awt.Rectangle


class EditorCellView(
  val editor: EditorImpl,
  val cell: EditorCell,
  private val cellInlayManager: NotebookCellInlayManager,
) : EditorCellViewComponent(), Disposable {
  val input: EditorCellInput = createEditorCellInput()

  var outputs: EditorCellOutputsView? = null
    private set

  val isSelected: Boolean
    get() = cell.isSelected.get()

  private val isHovered
    get() = cell.isHovered.get()

  var isUnderDiff: Boolean
    get() = cell.isUnderDiff.get()
    set(value) = cell.isUnderDiff.set(value)

  val controllers: List<SelfManagedCellController> by lazy {
    SelfManagedControllerFactory.createControllers(this)
  }

  private val cellHighlighters = mutableListOf<RangeHighlighter>()

  // We are storing last lines range for highlighters to prevent highlighters unnecessary recreation on the same lines.
  private var lastHighLightersLines: IntRange? = null


  init {
    cell.source.bind(this) {
      updateInput()
    }
    cell.isSelected.bind(this) { selected ->
      updateSelected()
    }
    editor.notebookAppearance.codeCellBackgroundColor.bind(this) { backgroundColor ->
      updateCellHighlight(force = true)
    }
    cell.notebook.showCellToolbar.bind(this) {
    }
    cell.isHovered.bind(this) {
      updateHovered()
    }

    updateSelfManaged()
    updateOutputs()
  }

  private fun updateSelected() {
    updateFolding()
    updateCellHighlight()
  }

  override fun dispose() {
    super.dispose()
    removeCellHighlight()
  }

  private fun createEditorCellInput(): EditorCellInput {
    val inputFactory = getInputFactories().firstOrNull { it.supports(editor, cell) } ?: TextEditorCellInputFactory()
    return EditorCellInput(inputFactory, cell).also {
      add(it)
    }
  }

  fun update(updateContext: UpdateContext) {
    input.updateInput()
    updateSelfManaged()
    updateOutputs()
    updateCellFolding(updateContext)
  }

  private fun updateSelfManaged() {
    controllers.forEach {
      it.selfUpdate()
    }
  }

  private fun updateInput() = runInEdt {
    updateCellHighlight()
    input.updateInput()
    checkAndRebuildInlays()
  }

  override fun doCheckAndRebuildInlays() {
    updateSelfManaged()
  }

  private fun updateOutputs() = runInEdt {
    if (hasOutputs()) {
      if (outputs == null) {
        outputs = EditorCellOutputsView(editor, cell).also {
          add(it)
        }
        updateCellHighlight()
        updateFolding()
      }
      else {
        outputs?.update()
      }
    }
    else {
      outputs?.let {
        remove(it)
        outputs = null
      }
    }
  }

  private fun hasOutputs() = cell.interval.type == NotebookCellLines.CellType.CODE
                             && (editor.editorKind != EditorKind.DIFF || Registry.`is`("jupyter.diff.viewer.output"))

  private fun getInputFactories(): Sequence<EditorCellInputFactory> = cellInlayManager.getInputFactories()

  fun onViewportChanges() {
    input.onViewportChange()
    outputs?.onViewportChange()
  }

  fun updateHovered() {
    updateFolding()
  }

  fun addCellHighlighter(provider: () -> RangeHighlighter) {
    val highlighter = provider()
    cellHighlighters.add(highlighter)
  }

  private fun removeCellHighlight() {
    cellHighlighters.forEach {
      it.dispose()
    }
    cellHighlighters.clear()
  }

  private fun updateCellHighlight(force: Boolean = false) {
    val interval = cell.interval

    if (!force && interval.lines == lastHighLightersLines) {
      return
    }
    lastHighLightersLines = IntRange(interval.lines.first, interval.lines.last)
    updateSelfManaged()

    removeCellHighlight()
  }


  private fun updateFolding() {
    input.folding.visible = isHovered || isSelected
    input.folding.selected = isSelected
    outputs?.foldingsVisible = isHovered || isSelected
    outputs?.foldingsSelected = isSelected
  }


  override fun calculateBounds(): Rectangle {
    val inputBounds = input.calculateBounds()
    val currentOutputs = outputs

    val outputRectangle = currentOutputs?.calculateBounds()?.takeIf { !it.isEmpty }
    val height = outputRectangle?.let { it.height + it.y - inputBounds.y } ?: inputBounds.height

    return Rectangle(0, inputBounds.y, editor.contentSize.width, height)
  }

  fun addDropHighlightIfApplicable() {
    controllers.filterIsInstance<DropHighlightable>().firstOrNull()?.addDropHighlight()
  }

  fun removeDropHighlightIfPresent() {
    controllers.filterIsInstance<DropHighlightable>().firstOrNull()?.removeDropHighlight()
  }
}