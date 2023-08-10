// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.hover

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent

fun addHoverAndPressStateListener(comp: JComponent,
                                  hoveredStateCallback: ((Component, Boolean) -> Unit)? = null,
                                  pressedStateCallback: ((Component, Boolean) -> Unit)? = null) {
  hoveredStateCallback?.let { callback ->
    val hoverListener = object : HoverStateListener() {
      override fun hoverChanged(component: Component, hovered: Boolean) = callback.invoke(component, hovered)
    }
    hoverListener.addTo(comp)
  }

  pressedStateCallback?.let { callback ->
    val pressListener = object : PressStateListener() {
      override fun pressedChanged(component: Component, pressed: Boolean) = callback.invoke(component, pressed)
    }
    pressListener.addTo(comp)
  }
}

private abstract class PressStateListener {

  abstract fun pressedChanged(component: Component, pressed: Boolean)

  fun addTo(component: JComponent, parent: Disposable? = null) {
    component.addMouseListener(mouseAdapter)
    parent?.let { Disposer.register(it, Disposable { removeFrom(component) }) }
  }

  fun removeFrom(component: JComponent) {
    component.removeMouseListener(mouseAdapter)
  }

  private val mouseAdapter = object: MouseAdapter() {
    override fun mousePressed(e: MouseEvent) = pressedChanged(e.component, true)
    override fun mouseReleased(e: MouseEvent) = pressedChanged(e.component, false)
  }

}