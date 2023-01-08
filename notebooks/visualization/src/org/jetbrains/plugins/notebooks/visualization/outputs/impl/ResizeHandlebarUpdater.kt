package org.jetbrains.plugins.notebooks.visualization.outputs.impl

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.ui.IdeBorderFactory
import org.jetbrains.plugins.notebooks.ui.visualization.notebookAppearance
import org.jetbrains.plugins.notebooks.visualization.outputs.impl.ResizeHandlebarUpdater.Companion.ensureInstalled
import java.awt.*
import java.awt.event.MouseEvent
import java.lang.ref.WeakReference
import javax.swing.border.Border

/**
 * A global event listener that draws a resizing handlebar below hovered cell outputs. Since cell outputs may contain non-Swing
 * components (like JCef), the usual MouseListener from Swing can't be used.
 *
 * Call [ensureInstalled] to initialize it.
 */
@Service
class ResizeHandlebarUpdater private constructor() : IdeEventQueue.EventDispatcher, Disposable {
  companion object {
    @JvmStatic
    fun ensureInstalled() {
      ApplicationManager.getApplication().assertIsDispatchThread()
      service<ResizeHandlebarUpdater>()
    }

    private val borderInsets = Insets(0, 0, 10, 0)

    @JvmField
    internal val invisibleResizeBorder = IdeBorderFactory.createEmptyBorder(borderInsets)
  }

  private val visibleResizeBorder = VisibleResizeBorder(this)

  private var currentCollapsingComponent = WeakReference<CollapsingComponent?>(null)

  init {
    IdeEventQueue.getInstance().addDispatcher(this, this)
  }

  override fun dispatch(e: AWTEvent): Boolean {
    when (e.id) {
      MouseEvent.MOUSE_MOVED, MouseEvent.MOUSE_WHEEL, MouseEvent.MOUSE_DRAGGED -> Unit
      else -> return false
    }
    if (e !is MouseEvent) return false

    setCurrentCollapsingComponent(getCollapsingComponentDepthFirst(e.component, e.x, e.y)?.takeIf { it.resizable })

    return false
  }

  override fun dispose(): Unit = Unit

  private fun getCollapsingComponentDepthFirst(component: Component, x: Int, y: Int): CollapsingComponent? =
    when {
      component !is Container || !component.isVisible || !component.contains(x, y) -> null

      component is CollapsingComponent -> component

      else ->
        component.components.firstNotNullOfOrNull { child ->
          val loc = child.location
          getCollapsingComponentDepthFirst(child, x - loc.x, y - loc.y)
        }
    }

  private fun setCurrentCollapsingComponent(new: CollapsingComponent?) {
    val old = currentCollapsingComponent.get()
    if (old != new) {
      old?.border = invisibleResizeBorder
      new?.border = visibleResizeBorder
      currentCollapsingComponent = WeakReference(new)
    }
  }

  private class VisibleResizeBorder(private val owner: ResizeHandlebarUpdater) : Border {
    override fun paintBorder(c: Component, g: Graphics, x: Int, y: Int, width: Int, height: Int) {
      g as Graphics2D
      g.color =
        owner.currentCollapsingComponent.get()?.editor?.run { notebookAppearance.getCodeCellBackground(colorsScheme) }
        ?: return

      g.stroke = BasicStroke(1.0f)

      val insets = getBorderInsets(c)
      assert(insets.top + insets.left + insets.right == 0)
      val yDraw = y + height - insets.bottom / 2
      g.drawLine(x, yDraw, x + width, yDraw)
    }

    override fun getBorderInsets(c: Component): Insets = borderInsets

    override fun isBorderOpaque(): Boolean = false
  }
}
