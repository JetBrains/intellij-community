package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.util.EventDispatcher
import org.jetbrains.plugins.notebooks.visualization.outputs.NotebookOutputInlayController
import org.jetbrains.plugins.notebooks.visualization.outputs.collapsingComponents
import java.awt.Dimension
import java.awt.Point

class EditorCellOutputs(editor: EditorEx, private val outputController: NotebookOutputInlayController) {

  private val cellEventListeners = EventDispatcher.create(EditorCellViewComponentListener::class.java)

  val outputs = outputController.collapsingComponents.map { EditorCellOutput(editor, it) }

  private val component = ControllerEditorCellViewComponent(outputController).also {
    it.addViewComponentListener(object : EditorCellViewComponentListener {
      override fun componentBoundaryChanged(location: Point, size: Dimension) {
        cellEventListeners.multicaster.componentBoundaryChanged(location, size)
        outputs.forEach { child -> child.updatePositions() }
      }
    })
  }

  val location: Point
    get() = component.location
  val size: Dimension
    get() = component.size

  fun dispose() {
    outputs.forEach { it.dispose() }
  }

  fun updatePositions() {
    component.updatePositions()
    outputs.forEach { it.updatePositions() }
  }

  fun onViewportChange() {
    outputController.onViewportChange()
    outputs.forEach { it.onViewportChange() }
  }

  fun updateSelection(selected: Boolean) {
    outputs.forEach { it.updateSelection(selected) }
  }

  fun showFolding() {
    outputs.forEach { it.showFolding() }
  }

  fun hideFolding() {
    outputs.forEach { it.hideFolding() }
  }

}