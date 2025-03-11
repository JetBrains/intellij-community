package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.ui.bind
import com.intellij.notebooks.visualization.outputs.NotebookOutputInlayShowable
import com.intellij.notebooks.visualization.outputs.impl.CollapsingComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.TestOnly
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities

class EditorCellOutputView internal constructor(
  private val editor: EditorImpl,
  private val output: EditorCellOutput,
  private val component: CollapsingComponent,
  private val toDispose: Disposable?,
) : EditorCellViewComponent() {

  var collapsed: Boolean
    get() = !component.isSeen
    set(value) {
      component.isSeen = !value
    }

  // Real UI Panel will be created lazily when folding became visible.
  val folding: EditorCellFoldingBar = EditorCellFoldingBar(editor, null, ::getFoldingBounds) {
    component.isSeen = !component.isSeen
  }
    .also {
      Disposer.register(this, it)
    }

  private val resizeListener = object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent) {
      saveSize()
    }
  }

  private fun saveSize() {
    val size = if (component.hasBeenManuallyResized) {
      component.customSize
    } else {
      component.calculateInnerSize()
    }
    output.size.set(EditorCellOutputSize(size, collapsed, component.maximized, component.hasBeenManuallyResized))
  }

  init {
    output.size.bind(this) { size ->
      collapsed = size.collapsed
      component.maximized = size.maximized
      if (size.resized) {
        component.customSize = size.size
        component.initialSize = null
      } else {
        component.customSize = null
        component.initialSize = size.size
      }
    }
    component.addComponentListener(resizeListener)
  }

  @TestOnly
  fun getOutputComponent(): JComponent = component.mainComponent

  private fun getFoldingBounds(): Pair<Int, Int> {
    val bounds = calculateBounds()
    return bounds.y to bounds.height
  }

  override fun dispose() {
    super.dispose()
    component.removeComponentListener(resizeListener)
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

    // Need to validate because a swing component can be invalid on update
    allCellOutputs.innerComponent.validate()

    val inlayBounds = allCellOutputs.inlay?.bounds ?: Rectangle(0, 0, 0, 0)
    val diffBetweenInternalAndExternal = allCellOutputs.innerComponent.location

    val location = component.location
    location.translate(inlayBounds.x + diffBetweenInternalAndExternal.x, inlayBounds.y + diffBetweenInternalAndExternal.y)
    return Rectangle(location, component.size)
  }
}