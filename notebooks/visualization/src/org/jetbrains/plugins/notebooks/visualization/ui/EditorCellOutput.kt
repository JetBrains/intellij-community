package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import com.intellij.ui.AncestorListenerAdapter
import com.intellij.ui.ComponentUtil
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.notebooks.ui.isFoldingEnabledKey
import org.jetbrains.plugins.notebooks.visualization.outputs.NotebookOutputComponentFactory.Companion.gutterPainter
import org.jetbrains.plugins.notebooks.visualization.outputs.NotebookOutputInlayShowable
import org.jetbrains.plugins.notebooks.visualization.outputs.impl.CollapsingComponent
import org.jetbrains.plugins.notebooks.visualization.outputs.impl.SurroundingComponent
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities
import javax.swing.event.AncestorEvent

val NOTEBOOK_CELL_OUTPUT_DATA_KEY = DataKey.create<EditorCellOutput>("NOTEBOOK_CELL_OUTPUT")

class EditorCellOutput internal constructor(
  private val editor: EditorEx,
  private val component: CollapsingComponent,
  private val toDispose: Disposable?,
) : EditorCellViewComponent() {

  var collapsed: Boolean
    get() = !component.isSeen
    set(value) {
      component.isSeen = !value
    }

  // Real UI Panel will be created lazily when folding became visible.
  val folding: EditorCellFoldingBar = EditorCellFoldingBar(editor, ::getFoldingBounds) { component.isSeen = !component.isSeen }

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

  @TestOnly
  fun getOutputComponent(): JComponent = component.mainComponent

  private fun getFoldingBounds(): Pair<Int, Int> {
    val inlayComponentLocation = SwingUtilities.convertPoint(component, Point(0, 0), editor.gutterComponentEx)
    return inlayComponentLocation.y to component.height
  }

  override fun doDispose() {
    folding.dispose()
    toDispose?.let { Disposer.dispose(it) }
  }

  override fun doViewportChange() {
    val component = component.mainComponent as? NotebookOutputInlayShowable ?: return
    if (component !is JComponent) return
    validateComponent(component)
    val componentRect = SwingUtilities.convertRectangle(component, component.bounds, editor.scrollPane.viewport.view)
    component.shown = editor.scrollPane.viewport.viewRect.intersects(componentRect)
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

  override fun calculateBounds(): Rectangle {
    val location = SwingUtilities.convertPoint(component.parent, component.location, editor.contentComponent)
    return Rectangle(location, component.size)
  }
}