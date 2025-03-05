package com.intellij.notebooks.visualization.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.TestOnly
import com.intellij.notebooks.visualization.outputs.NotebookOutputInlayShowable
import com.intellij.notebooks.visualization.outputs.impl.CollapsingComponent
import java.awt.Rectangle
import javax.swing.JComponent
import javax.swing.SwingUtilities

internal val NOTEBOOK_CELL_OUTPUT_DATA_KEY = DataKey.create<EditorCellOutputView>("NOTEBOOK_CELL_OUTPUT")

class EditorCellOutputView internal constructor(
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
  val folding: EditorCellFoldingBar = EditorCellFoldingBar(editor, ::getFoldingBounds) {
    component.isSeen = !component.isSeen
  }
    .also {
      Disposer.register(this, it)
    }

  @TestOnly
  fun getOutputComponent(): JComponent = component.mainComponent

  private fun getFoldingBounds(): Pair<Int, Int> {
    val bounds = calculateBounds()
    return bounds.y to bounds.height
  }

  override fun dispose() {
    super.dispose()
    toDispose?.let { Disposer.dispose(it) }
  }

  override fun doViewportChange() {
    val component = component.mainComponent as? NotebookOutputInlayShowable ?: return
    if (component !is JComponent) return
    val componentRect = SwingUtilities.convertRectangle(component, component.bounds, editor.scrollPane.viewport.view)
    component.shown = editor.scrollPane.viewport.viewRect.intersects(componentRect)
  }

  override fun calculateBounds(): Rectangle {
    val allCellOutputs = parent as? EditorCellOutputsView ?: return Rectangle(0, 0, 0, 0)

    //Need validate because swing component can be invalid on update
    allCellOutputs.innerComponent.validate()

    val inlayBounds = allCellOutputs.inlay?.bounds ?: Rectangle(0, 0, 0, 0)
    val diffBetweenInternalAndExternal = allCellOutputs.innerComponent.location

    val location = component.location
    location.translate(inlayBounds.x + diffBetweenInternalAndExternal.x, inlayBounds.y + diffBetweenInternalAndExternal.y)
    return Rectangle(location, component.size)
  }
}