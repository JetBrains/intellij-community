package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.util.EventDispatcher
import org.jetbrains.plugins.notebooks.visualization.outputs.NotebookOutputInlayController
import java.awt.Dimension
import java.awt.Point

internal class EditorCellOutput(editor: EditorEx, private val outputController: NotebookOutputInlayController) {

  private val cellEventListeners = EventDispatcher.create(EditorCellViewComponentListener::class.java)

  private val component = ControllerEditorCellViewComponent(outputController).also {
    it.addViewComponentListener(object : EditorCellViewComponentListener {
      override fun componentBoundaryChanged(location: Point, size: Dimension) {
        cellEventListeners.multicaster.componentBoundaryChanged(location, size)
      }
    })
  }

  val location: Point
    get() = component.location
  val size: Dimension
    get() = component.size

  private val folding = EditorCellFolding(editor) { outputController.toggle() }
    .also {
      cellEventListeners.addListener(object : EditorCellViewComponentListener {
        override fun componentBoundaryChanged(location: Point, size: Dimension) {
          it.updatePosition(location.y, size.height)
        }
      })
    }

  fun updatePositions() {
    component.updatePositions()
  }

  fun dispose() {
    folding.dispose()
  }

  fun onViewportChange() {
    outputController.onViewportChange()
  }

  fun hideFolding() {
    folding.hide()
  }

  fun showFolding() {
    folding.show()
  }

  fun updateSelection(value: Boolean) {
    folding.updateSelection(value)
  }

}