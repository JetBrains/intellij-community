package com.intellij.notebooks.visualization.ui

import com.intellij.openapi.Disposable
import com.intellij.util.EventDispatcher
import java.awt.AWTEvent
import java.awt.Component
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.ComponentEvent
import java.awt.event.InputEvent

/**
 * Dispatches mouse events (click, move, wheel) which have [target] component or any of its children.
 *
 * @property target the root Component for which the events will be collected.
 */
class NotebookAWTMouseDispatcher(private val target: Component) : Disposable, AWTEventListener {

  val eventDispatcher: EventDispatcher<AWTEventListener> = EventDispatcher.create(AWTEventListener::class.java)

  init {
    Toolkit.getDefaultToolkit().addAWTEventListener(this,
                                                    AWTEvent.MOUSE_WHEEL_EVENT_MASK or
                                                      AWTEvent.MOUSE_EVENT_MASK or
                                                      AWTEvent.MOUSE_MOTION_EVENT_MASK)
  }

  override fun dispose() {
    Toolkit.getDefaultToolkit().removeAWTEventListener(this)
  }

  override fun eventDispatched(event: AWTEvent) {
    if (event !is ComponentEvent) return

    var component: Component? = event.component
    while (component != null) {
      if (component == target) {
        if (event is InputEvent && !event.isConsumed) {
          eventDispatcher.getMulticaster().eventDispatched(event)
        }
      }
      component = component.getParent()
    }
  }
}