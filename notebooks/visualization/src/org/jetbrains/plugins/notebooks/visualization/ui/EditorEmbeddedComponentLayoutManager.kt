package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.Inlay
import com.intellij.ui.components.JBScrollPane
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager2
import java.awt.Rectangle
import java.util.HashMap
import javax.swing.JComponent
import javax.swing.JScrollPane
import kotlin.math.min

internal class EditorEmbeddedComponentLayoutManager(editorScrollPane: JScrollPane) : LayoutManager2 {
  private val constraints: MutableMap<JComponent?, Constraint?> = HashMap<JComponent?, Constraint?>()
  private val myEditorScrollPane: JScrollPane = editorScrollPane

  override fun addLayoutComponent(comp: Component?, constraints: Any?) {
    if (constraints !is Constraint) return
    this.constraints.put(comp as JComponent?, constraints)
  }

  override fun maximumLayoutSize(target: Container?): Dimension {
    return Dimension(Int.Companion.MAX_VALUE, Int.Companion.MAX_VALUE)
  }

  override fun getLayoutAlignmentX(target: Container?): Float {
    return 0f
  }

  override fun getLayoutAlignmentY(target: Container?): Float {
    return 0f
  }

  override fun invalidateLayout(target: Container?) {
  }

  override fun addLayoutComponent(name: String?, comp: Component?) {
    throw UnsupportedOperationException("Using string-based constraints is not supported.")
  }

  override fun removeLayoutComponent(comp: Component?) {
    this.constraints.remove(comp as JComponent?)
  }

  override fun preferredLayoutSize(parent: Container?): Dimension {
    return Dimension(0, 0)
  }

  override fun minimumLayoutSize(parent: Container?): Dimension {
    return Dimension(0, 0)
  }

  override fun layoutContainer(parent: Container) {
    synchronized(parent.treeLock) {
      val visibleWidth = maxOf(myEditorScrollPane.getViewport().getWidth() - myEditorScrollPane.getVerticalScrollBar().getWidth(), 0)
      for (entry in constraints.entries) {
        val component: JComponent = entry.key!!
        synchronizeBoundsWithInlay(entry.value!!, component, visibleWidth)
      }
    }
  }

  private fun synchronizeBoundsWithInlay(constraint: Constraint, component: JComponent, visibleWidth: Int) {
    val inlayBounds = constraint.getBounds()
    if (inlayBounds != null) {
      inlayBounds.setLocation(inlayBounds.x + verticalScrollbarLeftShift(), inlayBounds.y)
      val size = component.getPreferredSize()
      val newBounds = Rectangle(
        inlayBounds.x,
        inlayBounds.y,
        min((if (constraint.isFullWidth) visibleWidth else size.width).toDouble(), visibleWidth.toDouble()).toInt(),
        size.height
      )
      if (newBounds != component.bounds) {
        component.bounds = newBounds
        constraint.update()
      }
    }
  }

  private fun verticalScrollbarLeftShift(): Int {
    val flipProperty = myEditorScrollPane.getClientProperty(JBScrollPane.Flip::class.java)
    if (flipProperty === JBScrollPane.Flip.HORIZONTAL || flipProperty === JBScrollPane.Flip.BOTH) {
      return myEditorScrollPane.getVerticalScrollBar().getWidth()
    }
    return 0
  }

  sealed interface Constraint {

    fun getBounds(): Rectangle?

    fun update()

    val isFullWidth: Boolean

  }

  class InlayConstraint internal constructor(
    private val inlay: Inlay<*>,
    override val isFullWidth: Boolean,
  ) : Constraint {
    override fun getBounds(): Rectangle? {
      return inlay.bounds
    }

    override fun update() {
      inlay.update()
    }
  }

  class CustomFoldingConstraint internal constructor(
    private val customFoldRegion: CustomFoldRegion,
    override val isFullWidth: Boolean,
  ) : Constraint {
    override fun getBounds(): Rectangle? {
      return customFoldRegion.location?.let { Rectangle(it, Dimension(customFoldRegion.widthInPixels, customFoldRegion.heightInPixels)) }
    }

    override fun update() {
      customFoldRegion.update()
    }
  }
}