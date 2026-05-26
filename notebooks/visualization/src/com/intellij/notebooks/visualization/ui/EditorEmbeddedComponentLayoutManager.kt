package com.intellij.notebooks.visualization.ui

import com.intellij.notebooks.ui.visualization.NotebookUtil.overlappingVerticalScrollbarLeftShift
import com.intellij.notebooks.ui.visualization.NotebookUtil.visibleNotebookCellWidth
import com.intellij.notebooks.visualization.ui.providers.bounds.JupyterBoundsChangeNotifier
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.ex.EditorEx
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.LayoutManager2
import java.awt.Rectangle
import javax.swing.JComponent
import kotlin.math.min

internal class EditorEmbeddedComponentLayoutManager(private val editor: EditorEx) : LayoutManager2, Disposable {
  private val constraints: MutableList<Pair<JComponent, Constraint>> = mutableListOf()

  override fun dispose() {
    constraints.clear()
  }

  override fun addLayoutComponent(comp: Component, constraints: Any?) {
    if (constraints !is Constraint) return
    val insertIndex = this.constraints.binarySearchBy(constraints.order) { pair -> pair.second.order }.let { if (it < 0) -it - 1 else it }
    this.constraints.add(insertIndex, comp as JComponent to constraints)
  }

  override fun maximumLayoutSize(target: Container?): Dimension {
    return Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
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
    this.constraints.indexOfFirst { it.first == comp }
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
      editor.notebookEditorOrNull?.editorPositionKeeper?.keepScrollingPositionWhile {
        val visibleWidth = editor.visibleNotebookCellWidth()
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
      inlayBounds.setLocation(inlayBounds.x + editor.overlappingVerticalScrollbarLeftShift(), inlayBounds.y)
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

  sealed interface Constraint {

    fun getBounds(): Rectangle?

    fun update()

    val isFullWidth: Boolean

    val order: Int
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
      JupyterBoundsChangeNotifier.get(customFoldRegion.editor).boundsChanged()
    }

    override val order: Int
      get() = customFoldRegion.startOffset
  }

  class BlockInlayConstraint internal constructor(
    private val editor: EditorEx,
    private val inlay: Inlay<*>,
    override val isFullWidth: Boolean,
  ) : Constraint {
    override fun getBounds(): Rectangle? = inlay.bounds

    override fun update() {
      inlay.update()
      JupyterBoundsChangeNotifier.get(editor).boundsChanged()
    }

    override val order: Int
      get() = inlay.offset
  }
}
