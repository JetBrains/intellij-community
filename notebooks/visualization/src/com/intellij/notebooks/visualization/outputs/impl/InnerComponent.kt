package com.intellij.notebooks.visualization.outputs.impl

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.ui.GraphicsUtil
import com.intellij.notebooks.visualization.outputs.NotebookOutputComponentFactory
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager2
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.math.max
import kotlin.math.min

class InnerComponent : JPanel() {
  data class Constraint(val widthStretching: NotebookOutputComponentFactory.WidthStretching, val limitedHeight: Boolean)

  var maxHeight: Int
    get() = (layout as InnerComponentLayoutManager).maxHeight
    set(value) {
      (layout as InnerComponentLayoutManager).maxHeight = value
    }

  var scrollingEnabled: Boolean
    get() = (layout as InnerComponentLayoutManager).scrollingEnabled
    set(value) {
      (layout as InnerComponentLayoutManager).scrollingEnabled = value
    }

  init {
    isOpaque = false
    layout = InnerComponentLayoutManager()
  }

  override fun add(comp: Component, constraints: Any, index: Int) {
    require(comp is CollapsingComponent)
    require(constraints is Constraint)
    super.add(comp, constraints, index)
    if (GraphicsUtil.isRemoteEnvironment()) {
      (parent as SurroundingComponent).fireResize()
    }
  }

  val mainComponents: List<JComponent>
    get() = buildList<JComponent> {
      repeat(componentCount) { i ->
        add((getComponent(i) as CollapsingComponent).mainComponent)
      }
    }

  class InnerComponentLayoutManager : LayoutManager2 {

    var maxHeight: Int = Int.MAX_VALUE

    var scrollingEnabled: Boolean = true

    private val componentToConstraints = mutableMapOf<Component, Constraint>()

    override fun addLayoutComponent(comp: Component?, constraints: Any?) {
      if (comp != null) {
        if (constraints is Constraint) {
          componentToConstraints[comp] = constraints
        }
        else {
          componentToConstraints.remove(comp)
        }
      }
    }

    override fun maximumLayoutSize(target: Container): Dimension? {
      return foldSize(target) { minimumSize }
    }

    override fun getLayoutAlignmentX(target: Container?): Float = 0.5f

    override fun getLayoutAlignmentY(target: Container?): Float = 0.5f

    override fun invalidateLayout(target: Container?): Unit = Unit

    override fun addLayoutComponent(name: String?, comp: Component?): Unit = Unit

    override fun removeLayoutComponent(comp: Component?) {
      componentToConstraints.remove(comp)
    }

    override fun preferredLayoutSize(parent: Container): Dimension? {
      return foldSize(parent) { preferredSize }
    }

    override fun minimumLayoutSize(parent: Container): Dimension {
      return foldSize(parent) { minimumSize }
    }

    override fun layoutContainer(parent: Container) {
      val insets = parent.insets
      var totalY = insets.top
      forEveryComponent(parent, Component::getPreferredSize) { component, newWidth, newHeight ->
        component.setBounds(
          insets.left,
          totalY,
          newWidth,
          newHeight,
        )
        totalY += newHeight
      }

      if (GraphicsUtil.isRemoteEnvironment()) {
        (parent as? SurroundingComponent)?.fireResize()
      }
    }

    private inline fun foldSize(parent: Container, crossinline handler: Component.() -> Dimension): Dimension {
      val acc = Dimension(0, parent.insets.run { top + bottom })

      forEveryComponent(parent, handler) { _, newWidth, newHeight ->
        acc.width = max(acc.width, newWidth)
        acc.height += newHeight
      }

      return acc
    }

    private inline fun forEveryComponent(
      parent: Container,
      crossinline sizeProposer: Component.() -> Dimension,
      crossinline handleComponent: (component: Component, newWidth: Int, newHeight: Int) -> Unit,
    ) {
      repeat(parent.componentCount) { index ->
        val component = parent.getComponent(index)
        check(component is CollapsingComponent) { "$component is not CollapsingComponent" }
        val proposedSize = component.sizeProposer()
        val constraints = componentToConstraints[component]
        val newWidth = getComponentWidthByConstraint(parent, component, constraints, proposedSize.width)
        val limitHeight = scrollingEnabled && constraints?.limitedHeight == true
        val newHeight =
          if (!component.hasBeenManuallyResized && limitHeight) min(maxHeight, proposedSize.height)
          else proposedSize.height
        handleComponent(component, newWidth, newHeight)
      }
    }

    private fun getComponentWidthByConstraint(parent: Container, component: JComponent, constraint: Constraint?, componentDesiredWidth: Int): Int =
      (parent.width - parent.insets.left - parent.insets.right).let {
        when (constraint?.widthStretching) {
          NotebookOutputComponentFactory.WidthStretching.STRETCH_AND_SQUEEZE -> it
          NotebookOutputComponentFactory.WidthStretching.STRETCH -> max(it, componentDesiredWidth)
          NotebookOutputComponentFactory.WidthStretching.SQUEEZE -> min(it, componentDesiredWidth)
          NotebookOutputComponentFactory.WidthStretching.NOTHING -> componentDesiredWidth
          null -> {
            thisLogger().error("The component $component has no constraints")
            componentDesiredWidth
          }
        }
      }
  }
}
