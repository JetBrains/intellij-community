package org.jetbrains.plugins.notebooks.visualization.ui

import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ComponentUtil
import java.awt.AWTEvent
import java.awt.Component
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.JComponent
import javax.swing.JLayer
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import javax.swing.plaf.LayerUI
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

/**
 * Decorates component to handle nested scrolling areas gracefully.
 * As it described in [Mozilla documentation](https://wiki.mozilla.org/Gecko:Mouse_Wheel_Scrolling#Mouse_wheel_transaction)
 */
fun addNestedScrollingSupport(view: JComponent): JLayer<JComponent> {
  return JLayer(view, object : LayerUI<JComponent>() {

    private var _currentMouseWheelOwner: Component? = null
    private var currentMouseWheelOwner: Component?
      get() {
        return resetOwnerIfTimeoutExceeded()
      }
      set(value) {
        _currentMouseWheelOwner = value
      }

    private var timestamp = 0L

    private var dispatchingEvent: MouseEvent? = null

    override fun installUI(c: JComponent) {
      super.installUI(c)
      (c as JLayer<*>).layerEventMask = AWTEvent.MOUSE_WHEEL_EVENT_MASK or AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK
    }

    override fun uninstallUI(c: JComponent) {
      super.uninstallUI(c)
      (c as JLayer<*>).layerEventMask = 0
    }

    override fun processMouseWheelEvent(e: MouseWheelEvent, l: JLayer<out JComponent>) {
      val component = e.component
      if (isDispatchingInProgress()) {
        if (!isNewEventCreated(e)) {
          return
        }
        else if (_currentMouseWheelOwner != null) {
          // Prevents [JBScrollPane] from propagating wheel events to the parent component if there is an active scroll
          e.consume()
          return
        }
      }
      resetOwnerIfTimeoutExceeded()
      val owner = resetOwnerIfEventIsOutside(e)
      if (owner != null) {
        if (component != owner) {
          redispatchEvent(SwingUtilities.convertMouseEvent(component, e, owner))
          e.consume()
        }
        else {
          dispatchEvent(e)
        }
      }
    }

    private fun isNewEventCreated(e: MouseWheelEvent) = dispatchingEvent != e

    private fun isDispatchingInProgress() = dispatchingEvent != null

    private fun dispatchEvent(event: MouseEvent): Boolean {
      val oldDispatchingEvent = dispatchingEvent
      dispatchingEvent = event
      try {
        val owner = event.component
        owner.dispatchEvent(event)
        if (event.isConsumed && _currentMouseWheelOwner == null) {
          updateOwner(owner)
        }
        else {
          updateOwner(_currentMouseWheelOwner)
        }
        return event.isConsumed
      }
      finally {
        dispatchingEvent = oldDispatchingEvent
      }
    }

    private fun redispatchEvent(event: MouseEvent): Boolean {
      val oldDispatchingEvent = dispatchingEvent
      dispatchingEvent = null
      try {
        val owner = event.component
        owner.dispatchEvent(event)
        return event.isConsumed
      }
      finally {
        dispatchingEvent = oldDispatchingEvent
      }
    }

    private fun resetOwnerIfTimeoutExceeded(): Component? {
      val currentOwner = _currentMouseWheelOwner
      if (currentOwner == null) {
        return null
      }
      val scrollOwnerTimeout = Registry.intValue("jupyter.editor.scroll.mousewheel.timeout", 250).milliseconds
      return if (isTimeoutExceeded(scrollOwnerTimeout)) {
        resetOwner()
        null
      }
      else {
        currentOwner
      }
    }

    private fun resetOwnerIfEventIsOutside(e: MouseWheelEvent): Component? {
      val currentOwner = _currentMouseWheelOwner
      return if (currentOwner != null && isEventInsideOwner(currentOwner, e)) {
        currentOwner
      }
      else {
        resetOwner()
        e.component
      }
    }

    private fun isEventInsideOwner(owner: Component, e: MouseEvent): Boolean {
      val component = e.component
      return if (component == null) {
        false
      }
      else {
        val p = SwingUtilities.convertPoint(component, e.point, owner)
        return owner.contains(p)
      }
    }

    private fun isTimeoutExceeded(timeout: Duration): Boolean {
      return timestamp + timeout.toInt(DurationUnit.NANOSECONDS) < System.nanoTime()
    }

    private fun updateOwner(component: Component?) {
      if (component != null) {
        replaceOwner(component)
      }
      else {
        resetOwner()
      }
    }

    private fun replaceOwner(component: Component) {
      currentMouseWheelOwner = component
      timestamp = System.nanoTime()
    }

    private fun resetOwner() {
      timestamp = 0
      currentMouseWheelOwner = null
    }

    override fun processMouseEvent(e: MouseEvent, l: JLayer<out JComponent>) {
      if (e.id == MouseEvent.MOUSE_CLICKED || e.id == MouseEvent.MOUSE_RELEASED || e.id == MouseEvent.MOUSE_PRESSED) {
        val scrollPane = ComponentUtil.getParentOfType(JScrollPane::class.java, l.findComponentAt(e.point))
        updateOwner(scrollPane)
      }
    }

    override fun processMouseMotionEvent(e: MouseEvent, l: JLayer<out JComponent>) {
      val owner = currentMouseWheelOwner
      if (owner != null && isTimeoutExceeded(100.milliseconds) && !isEventInsideOwner(owner, e)) {
        resetOwner()
      }
    }
  })
}