package org.jetbrains.plugins.notebooks.visualization.outputs.impl

import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.ui.ComponentUtil
import com.intellij.util.concurrency.ThreadingAssertions
import org.jetbrains.plugins.notebooks.visualization.outputs.impl.ResizeHandlebarUpdater.Companion.ensureInstalled
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Container
import java.awt.event.MouseEvent
import java.lang.ref.WeakReference

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
      ThreadingAssertions.assertEventDispatchThread()
      service<ResizeHandlebarUpdater>()
    }
  }

  private var currentCollapsingComponent = WeakReference<CollapsingComponent?>(null)

  init {
    IdeEventQueue.getInstance().addDispatcher(this, this)
  }

  override fun dispatch(e: AWTEvent): Boolean {
    if ((e.id != MouseEvent.MOUSE_MOVED && e.id != MouseEvent.MOUSE_WHEEL) || e !is MouseEvent) {
      return false
    }

    setCurrentCollapsingComponent(getCollapsingComponentDepthFirst(e.component, e.x, e.y)?.takeIf { it.resizable })

    return false
  }

  override fun dispose() = Unit

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
      (old?.border as? CollapsingComponentBorder)?.resized = false
      (new?.border as? CollapsingComponentBorder)?.resized = true

      // We have overlapping elements over the CollapsingComponent - JupyterToolbar, and to draw it properly, we need to repaint the editor.
      val component = old ?: new
      ComponentUtil.getParentOfType(EditorComponentImpl::class.java, component)?.repaint()

      currentCollapsingComponent = WeakReference(new)
    }
  }
}