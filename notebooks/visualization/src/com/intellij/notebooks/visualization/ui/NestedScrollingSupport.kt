package com.intellij.notebooks.visualization.ui

import com.intellij.openapi.util.registry.Registry
import java.awt.Component
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.JLayer
import javax.swing.JScrollPane
import javax.swing.SwingUtilities
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

/**
 * Processes Mouse (wheel, motion, click) to handle nested scrolling areas gracefully. Used together with [NotebookAWTMouseDispatcher].
 * Nested scrolling idea described in [Mozilla documentation](https://wiki.mozilla.org/Gecko:Mouse_Wheel_Scrolling#Mouse_wheel_transaction)
 */
class NestedScrollingSupportImpl {

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

  private fun isNewEventCreated(e: MouseWheelEvent) = dispatchingEvent != e

  private fun isDispatchingInProgress() = dispatchingEvent != null

  fun processMouseWheelEvent(e: MouseWheelEvent) {
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

  fun processMouseEvent(e: MouseEvent, scrollPane: JScrollPane) {
    if (e.id == MouseEvent.MOUSE_CLICKED || e.id == MouseEvent.MOUSE_RELEASED || e.id == MouseEvent.MOUSE_PRESSED) {
      updateOwner(scrollPane)
    }
  }

  fun processMouseMotionEvent(e: MouseEvent) {
    val owner = currentMouseWheelOwner
    if (owner != null && isTimeoutExceeded(100.milliseconds) && !isEventInsideOwner(owner, e)) {
      resetOwner()
    }
  }

  private fun dispatchEvent(event: MouseEvent) {
    val owner = event.component
    if (isAsync(owner)) return

    if (owner is JLayer<*>) {
      val aa = owner.parent
      if (aa is JLayer<*>) {
        dispatchEventSync(event, aa.parent)
      }
      else {
        dispatchEventSync(event, aa)
      }
    }
    else {
      dispatchEventSync(event, owner)
    }
  }

  private fun dispatchEventSync(event: MouseEvent, owner: Component) {
    val oldDispatchingEvent = dispatchingEvent
    dispatchingEvent = event
    try {
      owner.dispatchEvent(event)
      if (event.isConsumed && _currentMouseWheelOwner == null) {
        updateOwner(owner)
      }
      else {
        updateOwner(_currentMouseWheelOwner)
      }
    }
    finally {
      dispatchingEvent = oldDispatchingEvent
    }
  }

  private fun redispatchEvent(event: MouseEvent) {
    val oldDispatchingEvent = dispatchingEvent
    dispatchingEvent = null
    try {
      val owner = event.component
      owner.dispatchEvent(event)
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
    val scrollOwnerTimeout = Registry.intValue("jupyter.editor.scroll.mousewheel.timeout", 750).milliseconds
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
    _currentMouseWheelOwner = component
    timestamp = System.nanoTime()
  }

  private fun resetOwner() {
    timestamp = 0
    _currentMouseWheelOwner = null
  }

  private fun isAsync(owner: Component): Boolean {
    return asyncComponents.contains(owner::class)
  }

  companion object {
    internal val asyncComponents = mutableSetOf<KClass<*>>()

    fun registerAsyncComponent(type: KClass<*>) {
      asyncComponents.add(type)
    }
  }
}