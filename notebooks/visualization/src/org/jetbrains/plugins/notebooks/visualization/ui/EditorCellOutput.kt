package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.editor.ex.EditorEx
import org.jetbrains.plugins.notebooks.visualization.outputs.impl.CollapsingComponent
import java.awt.Dimension
import java.awt.Point
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.SwingUtilities

class EditorCellOutput internal constructor(private val editor: EditorEx, private val component: CollapsingComponent) {

  val location: Point
    get() = SwingUtilities.convertPoint(component.parent, component.location, editor.contentComponent)
  val size: Dimension
    get() = component.size

  private val folding = EditorCellFolding(editor) { component.isSeen = !component.isSeen }
    .also {
      component.addComponentListener(object : ComponentAdapter() {
        override fun componentMoved(e: ComponentEvent) {
          updatePositions()
        }
        override fun componentResized(e: ComponentEvent) {
          updatePositions()
        }
      })
    }

  fun updatePositions() {
    folding.updatePosition(location.y, size.height)
  }

  fun dispose() {
    folding.dispose()
  }

  fun onViewportChange() {
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