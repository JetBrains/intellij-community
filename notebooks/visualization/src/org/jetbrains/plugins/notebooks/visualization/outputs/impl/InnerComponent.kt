package org.jetbrains.plugins.notebooks.visualization.outputs.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.impl.EditorImpl
import org.jetbrains.plugins.notebooks.visualization.SwingClientProperty
import org.jetbrains.plugins.notebooks.visualization.notebookCellEditorScrollingPositionKeeper
import org.jetbrains.plugins.notebooks.visualization.outputs.NotebookOutputComponentFactory
import java.awt.Component
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.max
import kotlin.math.min

internal class InnerComponent(private val editor: EditorImpl) : JPanel() {
  data class Constraint(val widthStretching: NotebookOutputComponentFactory.WidthStretching, val limitedHeight: Boolean)

  var maxHeight: Int = Int.MAX_VALUE

  override fun updateUI() {
    super.updateUI()
    isOpaque = false
  }

  override fun add(comp: Component, constraints: Any, index: Int) {
    require(comp is CollapsingComponent)
    require(constraints is Constraint)
    comp.layoutConstraints = constraints
    super.add(comp, constraints, index)
  }

  override fun remove(index: Int) {
    (getComponent(index) as JComponent).layoutConstraints = null
    super.remove(index)
  }

  val mainComponents: List<JComponent>
    get() = ArrayList<JComponent>(componentCount).also {
      repeat(componentCount) { i ->
        it += (getComponent(i) as CollapsingComponent).mainComponent
      }
    }

  override fun getPreferredSize(): Dimension =
    foldSize { preferredSize }

  override fun getMinimumSize(): Dimension =
    foldSize { minimumSize }

  override fun getMaximumSize(): Dimension =
    foldSize { maximumSize }

  override fun doLayout() {
    val oldComponentHeights = components.sumBy { it.height }

    var totalY = insets.top
    forEveryComponent(Component::getPreferredSize) { component, newWidth, newHeight ->
      component.setBounds(
        insets.left,
        totalY,
        newWidth,
        newHeight,
      )
      totalY += newHeight
    }

    val newComponentHeights = components.sumBy { it.height }
    if (oldComponentHeights != newComponentHeights) {
      editor.notebookCellEditorScrollingPositionKeeper?.adjustScrollingPosition()
    }
  }

  private inline fun foldSize(crossinline handler: Component.() -> Dimension): Dimension {
    val acc = Dimension(0, insets.run { top + bottom })

    forEveryComponent(handler) { _, newWidth, newHeight ->
      acc.width = max(acc.width, newWidth)
      acc.height += newHeight
    }

    return acc
  }

  private inline fun forEveryComponent(
    crossinline sizeProposer: Component.() -> Dimension,
    crossinline handleComponent: (component: Component, newWidth: Int, newHeight: Int) -> Unit,
  ) {
    repeat(componentCount) { index ->
      val component = getComponent(index)
      check(component is CollapsingComponent) { "$component is not CollapsingComponent" }
      val proposedSize = component.sizeProposer()
      val newWidth = getComponentWidthByConstraint(component, proposedSize.width)
      val newHeight =
        if (!component.isPreferredSizeSet && component.layoutConstraints?.limitedHeight == true) min(maxHeight, proposedSize.height)
        else proposedSize.height
      handleComponent(component, newWidth, newHeight)
    }
  }

  private fun getComponentWidthByConstraint(component: JComponent, componentDesiredWidth: Int): Int =
    (width - insets.left - insets.right).let {
      when (component.layoutConstraints?.widthStretching) {
        NotebookOutputComponentFactory.WidthStretching.STRETCH_AND_SQUEEZE -> it
        NotebookOutputComponentFactory.WidthStretching.STRETCH -> max(it, componentDesiredWidth)
        NotebookOutputComponentFactory.WidthStretching.SQUEEZE -> min(it, componentDesiredWidth)
        NotebookOutputComponentFactory.WidthStretching.NOTHING -> componentDesiredWidth
        null -> {
          LOG.error("The component $component has no constraints")
          componentDesiredWidth
        }
      }
    }

  private var JComponent.layoutConstraints: Constraint? by SwingClientProperty("layoutConstraints")

  companion object {
    private val LOG = logger<InnerComponent>()
  }
}