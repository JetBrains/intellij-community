package org.jetbrains.plugins.notebooks.visualization.ui

import java.awt.AWTEvent
import java.awt.Component
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.JComponent
import javax.swing.JLayer
import javax.swing.SwingUtilities
import javax.swing.plaf.LayerUI
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

/**
 * Decorates component to handle nested scrolling areas gracefully.
 * As it described in https://wiki.mozilla.org/Gecko:Mouse_Wheel_Scrolling#Mouse_wheel_transaction
 */
fun addNestedScrollingSupport(view: JComponent): JLayer<JComponent> {
  return JLayer(view, object : LayerUI<JComponent>() {

    private var currentMouseWheelOwner: Component? = null

    private var timestamp = 0L

    private var redispatchingInProgress = false

    override fun installUI(c: JComponent) {
      super.installUI(c)
      (c as JLayer<*>).layerEventMask = AWTEvent.MOUSE_WHEEL_EVENT_MASK or AWTEvent.MOUSE_EVENT_MASK or AWTEvent.MOUSE_MOTION_EVENT_MASK
    }

    override fun uninstallUI(c: JComponent) {
      super.uninstallUI(c)
      (c as JLayer<*>).layerEventMask = 0
    }

    override fun processMouseWheelEvent(e: MouseWheelEvent, l: JLayer<out JComponent>) {
      if (redispatchingInProgress) {
        return
      }
      val owner = getValidOwner(e)
      if (currentMouseWheelOwner != owner) {
        updateOwner(owner)
      }
      else {
        timestamp = System.nanoTime()
      }
      if (owner != null) {
        val component = e.component
        if (owner != component) {
          e.consume()
          redispatchingInProgress = true
          try {
            owner.dispatchEvent(SwingUtilities.convertMouseEvent(component, e, owner))
          }
          finally {
            redispatchingInProgress = false
          }
        }
      }
    }


    private fun getValidOwner(e: MouseWheelEvent): Component? {
      val component = e.component
      return if (component == null) {
        null
      }
      else {
        if (isTimeoutExceeded(1500.milliseconds)) {
          component
        }
        else {
          if (isEventInsideOwner(e)) {
            currentMouseWheelOwner
          }
          else {
            component
          }
        }
      }
    }

    private fun isEventInsideOwner(e: MouseEvent): Boolean {
      val owner = currentMouseWheelOwner
      val component = e.component
      return if (owner == null || component == null) {
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
        if (isEventInsideOwner(e)) {
          resetOwner()
        }
      }
    }

    override fun processMouseMotionEvent(e: MouseEvent, l: JLayer<out JComponent>) {
      if (isTimeoutExceeded(100.milliseconds) && !isEventInsideOwner(e)) {
        resetOwner()
      }
    }
  })
}