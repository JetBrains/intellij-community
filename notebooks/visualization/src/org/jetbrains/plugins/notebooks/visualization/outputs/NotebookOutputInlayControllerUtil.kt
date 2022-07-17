package org.jetbrains.plugins.notebooks.visualization.outputs

import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.notebooks.visualization.SwingClientProperty
import org.jetbrains.plugins.notebooks.visualization.outputs.impl.CollapsingComponent
import org.jetbrains.plugins.notebooks.visualization.outputs.impl.InnerComponent
import org.jetbrains.plugins.notebooks.visualization.outputs.impl.SurroundingComponent
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JScrollPane
import javax.swing.event.ChangeEvent
import javax.swing.event.ChangeListener
import kotlin.math.max

internal var EditorGutterComponentEx.hoveredCollapsingComponentRect: CollapsingComponent? by SwingClientProperty("hoveredCollapsingComponentRect")

// TODO It severely breaks encapsulation. At least, we should cover it with tests.
internal val NotebookOutputInlayController.collapsingComponents: List<CollapsingComponent>
  get() = inlay
    .renderer
    .let { (it as JComponent).getComponent(0)!! }
    .let { it as SurroundingComponent }
    .let { (it.layout as BorderLayout).getLayoutComponent(BorderLayout.CENTER) }
    .let { it as InnerComponent }
    .components
    .map { it as CollapsingComponent }

val NotebookOutputInlayController.outputComponents: List<JComponent>
  @TestOnly get() = collapsingComponents.map { it.mainComponent }

/**
 * [component] is any component that belongs to an output inlay.
 * If the component is null or seems to be not inside an output inlay, nothing happens.
 */
fun resetOutputInlayCustomHeight(component: Component?) {
  generateSequence(component, Component::getParent)
    .filterIsInstance<CollapsingComponent>()
    .firstOrNull()
    ?.resetCustomHeight()
}

/**
 * Scrolls the pane down in case of various size changes, if the pane was scrolled down before the size change.
 *
 * If the pane wasn't scrolled to the bottom (i.e. the last pixel of the view wasn't seen), the scroll remains unchanged.
 */
fun installAutoScrollToBottom(scrollPane: JScrollPane) {
  var wasScrolledToBottom = true

  val viewport = scrollPane.viewport
  val view = viewport.view

  val scrollToBottomListener = object : ComponentAdapter() {
    override fun componentResized(e: ComponentEvent) {
      if (wasScrolledToBottom) {
        scrollPane.verticalScrollBar.value = max(0, view.height - viewport.viewRect.height)
      }
    }
  }

  viewport.addComponentListener(scrollToBottomListener)
  view.addComponentListener(scrollToBottomListener)

  scrollPane.verticalScrollBar.model.addChangeListener(object : ChangeListener {
    var oldValue = scrollPane.verticalScrollBar.value

    override fun stateChanged(e: ChangeEvent) {
      val newValue = scrollPane.verticalScrollBar.value
      if (oldValue != newValue) {
        oldValue = newValue
        wasScrolledToBottom = scrollPane.verticalScrollBar.value + viewport.viewRect.height >= view.height
      }
    }
  })
}