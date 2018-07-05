// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui.customFrameDecorations.style

import java.awt.MouseInfo
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.beans.PropertyChangeEvent
import javax.swing.JComponent
import javax.swing.SwingUtilities

class ComponentStyle<T : JComponent>(val default: Properties) {
  constructor(init: Properties.() -> Unit) : this(Properties().apply { init() })

  private val styleMap: HashMap<ComponentStyleState, Properties> = HashMap()

  private class ComponentState(val base: Properties) {
    var hovered = false
    var pressed = false
  }

  private fun createListener(component: T, componentState: ComponentState) = object : MouseAdapter() {
    override fun mouseReleased(e: MouseEvent) {
      componentState.pressed = false
      updateStyle()
    }

    override fun mouseEntered(e: MouseEvent) {
      componentState.hovered = true
      updateStyle()
    }

    override fun mouseExited(e: MouseEvent) {
      componentState.hovered = false
      updateStyle()
    }

    override fun mousePressed(e: MouseEvent) {
      componentState.pressed = true
      updateStyle()
    }

    private fun updateStyle() {
      updateStyle(component, componentState)
    }
  }

  fun clone(): ComponentStyle<T> {
    val style = ComponentStyle<T>(default.clone())

    for ((k, v) in styleMap) {
      style.styleMap[k] = v.clone()
    }

    return style
  }


  fun style(state: ComponentStyleState, init: Properties.() -> Unit) {
    val prop = Properties()
    prop.init()
    styleMap[state] = prop
  }

  fun updateDefault(init: Properties.() -> Unit) {
    val prop = Properties()
    prop.init()
    default.updateBy(prop)
  }

  fun updateState(state: ComponentStyleState, init: Properties.() -> Unit) {
    val prop = Properties()
    prop.init()
    styleMap[state]?.updateBy(prop)
    if (styleMap[state] == null) {
      styleMap[state] = prop
    }
  }

  private fun isMouseOver(component: T): Boolean {
    val location = MouseInfo.getPointerInfo()?.location
    if (location != null) {
      SwingUtilities.convertPointFromScreen(location, component)
      return component.contains(location)
    }
    return false
  }

  internal fun applyStyleSnapshot(component: T): RemoveStyleListener {
    val base = StyleProperty.getPropertiesSnapshot(component)
    val baseClone = base.clone().updateBy(default)
    baseClone.applyTo(component)

    val componentState = ComponentState(baseClone).apply {
      hovered = isMouseOver(component)
      pressed = false
    }
    val mouseListener = createListener(component, componentState)
    val enabledListener: (PropertyChangeEvent) -> Unit = {
      checkState(component, componentState, mouseListener)
    }
    component.addPropertyChangeListener("enabled", enabledListener)

    checkState(component, componentState, mouseListener)

    return object : RemoveStyleListener {
      override fun remove() {
        base.applyTo(component)
        component.removeMouseListener(mouseListener)
        component.removePropertyChangeListener(enabledListener)
      }
    }
  }

  private fun checkState(
    component: T,
    componentState: ComponentState,
    mouseListener: MouseAdapter
  ) {
    if (component.isEnabled) {
      if (ComponentStyleState.HOVERED in styleMap || ComponentStyleState.PRESSED in styleMap) {
        componentState.hovered = isMouseOver(component)
        componentState.pressed = false
        component.addMouseListener(mouseListener)
      }
    }
    else {
      component.removeMouseListener(mouseListener)
    }
    updateStyle(component, componentState)
  }

  private fun updateStyle(component: T, componentState: ComponentState) {
    val properties = componentState.base.clone()
    if (!component.isEnabled) {
      if (ComponentStyleState.DISABLED in styleMap) properties.updateBy(styleMap[ComponentStyleState.DISABLED]!!)
      properties.applyTo(component)
      return
    }
    if (componentState.hovered && ComponentStyleState.HOVERED in styleMap) properties.updateBy(styleMap[ComponentStyleState.HOVERED]!!)
    if (componentState.pressed && ComponentStyleState.PRESSED in styleMap) properties.updateBy(styleMap[ComponentStyleState.PRESSED]!!)
    properties.applyTo(component)
  }
}

interface RemoveStyleListener {
    fun remove()
}

enum class ComponentStyleState {
  HOVERED, PRESSED, DISABLED
}

