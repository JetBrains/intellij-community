package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.notebooks.ui.isFoldingEnabledKey
import org.jetbrains.plugins.notebooks.visualization.outputs.NotebookOutputComponentFactory.Companion.gutterPainter
import org.jetbrains.plugins.notebooks.visualization.outputs.NotebookOutputInlayShowable
import org.jetbrains.plugins.notebooks.visualization.outputs.impl.CollapsingComponent
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities

val NOTEBOOK_CELL_OUTPUT_DATA_KEY = DataKey.create<EditorCellOutput>("NOTEBOOK_CELL_OUTPUT")

class EditorCellOutput internal constructor(private val editor: EditorEx, private val component: CollapsingComponent, private val disposable: Disposable?) {

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
    disposable?.let { Disposer.dispose(it) }
  }

  fun onViewportChange() {
      val component = component.mainComponent as? NotebookOutputInlayShowable ?: return
      if (component !is JComponent) return
      validateComponent(component)
      val componentRect = SwingUtilities.convertRectangle(component, component.bounds, editor.scrollPane.viewport.view)
      component.shown = editor.scrollPane.viewport.viewRect.intersects(componentRect)
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

  fun paintGutter(editor: EditorImpl, yOffset: Int, g: Graphics, r: Rectangle) {
    if (editor.getUserData(isFoldingEnabledKey) != true) {
      component.paintGutter(editor, yOffset, g)
    }
    val mainComponent = component.mainComponent

    mainComponent.gutterPainter?.let { painter ->
      mainComponent.yOffsetFromEditor(editor)?.let { yOffset ->
        val bounds = Rectangle(r.x, yOffset, r.width, mainComponent.height)
        painter.paintGutter(editor, g, bounds)
      }
    }
  }
}