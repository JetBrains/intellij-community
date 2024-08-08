package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.notebooks.ui.isFoldingEnabledKey
import org.jetbrains.plugins.notebooks.visualization.outputs.NotebookOutputComponentFactory.Companion.gutterPainter
import org.jetbrains.plugins.notebooks.visualization.outputs.NotebookOutputInlayShowable
import org.jetbrains.plugins.notebooks.visualization.outputs.impl.CollapsingComponent
import java.awt.Graphics
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.SwingUtilities

val NOTEBOOK_CELL_OUTPUT_DATA_KEY = DataKey.create<EditorCellOutput>("NOTEBOOK_CELL_OUTPUT")

class EditorCellOutput internal constructor(
  private val editor: EditorImpl,
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

  @TestOnly
  fun getOutputComponent(): JComponent = component.mainComponent

  private fun getFoldingBounds(): Pair<Int, Int> {
    val bounds = calculateBounds()
    return bounds.y to bounds.height
  }

  override fun doDispose() {
    folding.dispose()
    toDispose?.let { Disposer.dispose(it) }
  }

  override fun doViewportChange() {
    val component = component.mainComponent as? NotebookOutputInlayShowable ?: return
    if (component !is JComponent) return
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
    val allCellOutputs = parent as? EditorCellOutputs ?: return Rectangle(0, 0, 0, 0)

    //Need validate because swing component can be invalid on update
    allCellOutputs.innerComponent.validate()

    val inlayBounds = allCellOutputs.inlay?.bounds ?: Rectangle(0, 0, 0, 0)
    val diffBetweenInternalAndExternal = allCellOutputs.innerComponent.location

    val location = component.location
    location.translate(inlayBounds.x + diffBetweenInternalAndExternal.x, inlayBounds.y + diffBetweenInternalAndExternal.y)
    return Rectangle(location, component.size)
  }
}