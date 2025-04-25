package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.visualization.ui.providers.bounds.JupyterBoundsChangeHandler
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.ui.components.JBScrollPane
import java.awt.*
import javax.swing.JComponent
import javax.swing.JScrollPane
import kotlin.math.min

internal class EditorEmbeddedComponentLayoutManager(private val editor: EditorEx) : LayoutManager2 {
  private val constraints: MutableList<Pair<JComponent, Constraint>> = mutableListOf()
  private val myEditorScrollPane: JScrollPane
    get() = editor.scrollPane

  override fun addLayoutComponent(comp: Component, constraints: Any?) {
    if (constraints !is Constraint) return
    val insertIndex = this.constraints.binarySearchBy(constraints.order) { pair -> pair.second.order }.let { if (it < 0) -it - 1 else it }
    this.constraints.add(insertIndex, comp as JComponent to constraints)
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
    this.constraints.indexOfFirst { it.second == comp }
      .takeIf { it >= 0 }
      ?.let { this.constraints.removeAt(it) }
  }

  override fun preferredLayoutSize(parent: Container?): Dimension {
    return Dimension(0, 0)
  }

  override fun minimumLayoutSize(parent: Container?): Dimension {
    return Dimension(0, 0)
  }

  override fun layoutContainer(parent: Container) {
    synchronized(parent.treeLock) {
      editor.notebookEditor.editorPositionKeeper.keepScrollingPositionWhile {
        val visibleWidth = maxOf(myEditorScrollPane.getViewport().getWidth() - myEditorScrollPane.getVerticalScrollBar().getWidth(), 0)
        for (entry in constraints) {
          val component: JComponent = entry.first
          synchronizeBoundsWithInlay(entry.second, component, visibleWidth)
        }
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
        min((if (constraint.isFullWidth) visibleWidth else size.width), visibleWidth),
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

    val order: Int

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

    override val order: Int
      get() = inlay.offset
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
      // [com.intellij.openapi.editor.impl.FoldingModelImpl.onCustomFoldRegionPropertiesChange] fires listeners
      // before make model consistent.
      // Here we have to call it once again to get correct bounds.
      JupyterBoundsChangeHandler.get(customFoldRegion.editor).boundsChanged()
    }

    override val order: Int
      get() = customFoldRegion.startOffset
  }
}