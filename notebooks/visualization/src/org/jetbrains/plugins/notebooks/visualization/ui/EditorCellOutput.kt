package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.ide.DataManager
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.editor.ex.EditorEx
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.notebooks.visualization.outputs.impl.CollapsingComponent
import java.awt.Dimension
import java.awt.Point
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities

val NOTEBOOK_CELL_OUTPUT_DATA_KEY = DataKey.create<EditorCellOutput>("NOTEBOOK_CELL_OUTPUT")

class EditorCellOutput internal constructor(private val editor: EditorEx, private val component: CollapsingComponent) {

  val location: Point
    get() = SwingUtilities.convertPoint(component.parent, component.location, editor.contentComponent)
  val size: Dimension
    get() = component.size
  var collapsed: Boolean
    get() = !component.isSeen
    set(value) {
      component.isSeen = !value
    }

  @TestOnly
  fun getOutputComponent(): JComponent = component.mainComponent

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

  init {
    if (DataManager.getDataProvider(component) == null) {
      DataManager.registerDataProvider(component) { key ->
        when (key) {
          NOTEBOOK_CELL_OUTPUT_DATA_KEY.name -> this@EditorCellOutput
          else -> null
        }
      }
    }
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