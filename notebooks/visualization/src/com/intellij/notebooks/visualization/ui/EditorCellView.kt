package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.ui.bind
import com.intellij.notebooks.visualization.NotebookCellLines
import com.intellij.notebooks.visualization.UpdateContext
import com.intellij.notebooks.visualization.controllers.selfUpdate.SelfManagedCellController
import com.intellij.notebooks.visualization.controllers.selfUpdate.SelfManagedControllerFactory
import com.intellij.notebooks.visualization.ui.cellsDnD.DropHighlightable
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.util.registry.Registry
import java.awt.Rectangle


class EditorCellView(val cell: EditorCell) : EditorCellViewComponent() {
  private val editor = cell.editor

  val input: EditorCellInput = EditorCellInput(cell).also {
    add(it)
  }
  var outputs: EditorCellOutputsView? = null
    private set

  val isSelected: Boolean
    get() = cell.isSelected.get()

  private val isHovered
    get() = cell.isHovered.get()

  val controllers: List<SelfManagedCellController> by lazy {
    SelfManagedControllerFactory.createControllers(this)
  }

  init {
    cell.source.bind(this) {
      updateInput()
    }
    cell.isSelected.bind(this) { selected ->
      updateFolding()
    }
    cell.isHovered.bind(this) {
      updateHovered()
    }

    updateOutputs()
    checkAndRebuildInlays()
  }

  fun update(updateContext: UpdateContext) {
    input.updateInput()
    updateOutputs()
    updateCellFolding(updateContext)
  }

  private fun updateInput() {
    input.updateInput()
  }

  override fun doCheckAndRebuildInlays() {
    controllers.forEach {
      it.checkAndRebuildInlays()
    }
    cell.cellFrameManager?.updateCellFrameShow()
  }

  private fun updateOutputs() = runInEdt {
    if (hasOutputs()) {
      if (outputs == null) {
        outputs = EditorCellOutputsView(editor, cell).also {
          add(it)
        }
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

  fun onViewportChanges() {
    input.onViewportChange()
    outputs?.onViewportChange()
  }

  fun updateHovered() {
    updateFolding()
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