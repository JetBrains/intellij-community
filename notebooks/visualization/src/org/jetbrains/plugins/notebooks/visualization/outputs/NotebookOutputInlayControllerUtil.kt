package org.jetbrains.plugins.notebooks.visualization.outputs

import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import org.jetbrains.annotations.TestOnly
import org.jetbrains.plugins.notebooks.visualization.SwingClientProperty
import org.jetbrains.plugins.notebooks.visualization.outputs.impl.CollapsingComponent
import org.jetbrains.plugins.notebooks.visualization.outputs.impl.InnerComponent
import org.jetbrains.plugins.notebooks.visualization.outputs.impl.SurroundingComponent
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.JComponent

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