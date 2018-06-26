// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui.customFrameDecorations.style

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Disposer
import java.awt.Color
import java.awt.Insets
import java.awt.MouseInfo
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.beans.PropertyChangeEvent
import javax.swing.*
import javax.swing.border.Border
import javax.swing.plaf.basic.BasicButtonUI

/**
 * @author graann on 22/06/2018
 */
class StyleManager {
  companion object {
    private const val STYLE_PROPERTY = "STYLE_PROPERTY"
    fun <T : JComponent> applyStyle(component: T, style: ComponentStyle<T>) {
      removeStyle(component)
      val disposable = style.applyStyle(component)
      component.putClientProperty(STYLE_PROPERTY, disposable)
    }

    fun <T : JComponent> removeStyle(component: T) {
      val disposable = component.getClientProperty(STYLE_PROPERTY)
      if (disposable != null && disposable is Disposable) {
        Disposer.dispose(disposable)
      }
    }
  }
}

class ComponentStyle<T : JComponent>(private val default: Properties, private val styleMap: Map<States, Properties> = HashMap()) :
  Disposable {
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

  private fun isMouseOver(component: T): Boolean {
    val location = MouseInfo.getPointerInfo()?.location
    if (location != null) {
      SwingUtilities.convertPointFromScreen(location, component)
      return component.contains(location)
    }
    return false
  }

  internal fun applyStyle(component: T): Disposable? {
    val styleDisposable = Disposer.newDisposable()
    default.applyTo(component)

    val base = StyleProperty.getPropertiesSnapshot(component)
    Disposer.register(styleDisposable, Disposable {
      base.applyTo(component)
    })

    val componentState = ComponentState(base).apply {
      hovered = isMouseOver(component)
      pressed = false
    }
    val mouseListener = createListener(component, componentState)
    Disposer.register(styleDisposable, Disposable {
      component.removeMouseListener(mouseListener)
    })
    val enabledListener: (PropertyChangeEvent) -> Unit = {
      checkMouse(component, componentState, mouseListener)
    }
    component.addPropertyChangeListener("enabled", enabledListener)
    Disposer.register(styleDisposable, Disposable {
      component.removePropertyChangeListener(enabledListener)
    })
    checkMouse(component, componentState, mouseListener)
    return styleDisposable
  }

  private fun checkMouse(
    component: T,
    componentState: ComponentState,
    mouseListener: MouseAdapter
  ) {
    if (component.isEnabled) {
      if (styleMap.containsKey(States.HOVERED) || styleMap.containsKey(States.PRESSED)) {
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
      if (styleMap.containsKey(States.DISABLED)) properties.updateBy(styleMap[States.DISABLED]!!)
      properties.applyTo(component)
      return
    }
    if (componentState.hovered && styleMap.containsKey(States.HOVERED)) properties.updateBy(styleMap[States.HOVERED]!!)
    if (componentState.pressed && styleMap.containsKey(States.PRESSED)) properties.updateBy(styleMap[States.PRESSED]!!)
    properties.applyTo(component)
  }

  override fun dispose() {
  }
}

enum class States {
  HOVERED, PRESSED, DISABLED
}

sealed class StyleProperty(
  private val setProperty: (JComponent, Any?) -> Unit,
  val getProperty: (JComponent) -> Any?,
  private val valueType: Class<out Any?>,
  val componentType: Class<out Any?> = JComponent::class.java
) {
  companion object {
    fun getPropertiesSnapshot(component: JComponent): Properties {
      val base = Properties()
      for (p in arrayOf(FOREGROUND, BACKGROUND, OPAQUE, BORDER, ICON, MARGIN)) {
        if (p.componentType.isInstance(component))
          base.setValue(p, p.getProperty(component))
      }
      return base
    }
  }

  object OPAQUE : StyleProperty(
    { component, isOpaque -> component.isOpaque = if (isOpaque == null) true else isOpaque as Boolean },
    { component -> component.isOpaque },
    Boolean::class.java
  )

  object BACKGROUND : StyleProperty(
    { component, background -> component.background = if (background == null) null else background as Color },
    { component -> component.background },
    Color::class.java
  )

  object FOREGROUND : StyleProperty(
    { component, foreground -> component.foreground = if (foreground == null) null else foreground as Color },
    { component -> component.foreground },
    Color::class.java
  )

  object BORDER : StyleProperty(
    { component, border -> component.border = if (border == null) null else border as Border },
    { component -> component.border },
    Border::class.java
  )

  object ICON : StyleProperty(
    { component, icon -> (component as AbstractButton).icon = if (icon == null) null else icon as Icon },
    { component -> (component as AbstractButton).icon },
    Icon::class.java,
    AbstractButton::class.java
  )

  object MARGIN : StyleProperty(
    { component, margin -> (component as AbstractButton).margin = if (margin == null) null else margin as Insets },
    { component -> (component as AbstractButton).margin },
    Insets::class.java,
    AbstractButton::class.java
  )

  protected val log = Logger.getInstance(StyleProperty::class.java)
  protected fun checkTypes(component: JComponent, value: Any?): Boolean {
    if(!componentType.isInstance(component)) {
      log.warn(javaClass.canonicalName+" Incorrect class type: "+component.javaClass.canonicalName+" instead of "+componentType.canonicalName)
      return false
    }

    if(valueType == Boolean::class.java) {
      return (value == true || value == false)
    }

    if(!(value == null || valueType.isInstance(value))) {
      log.warn(javaClass.canonicalName+" Incorrect value type: "+value.javaClass.canonicalName+" instead of "+valueType.canonicalName)
      return false
    }
    return true
  }

  fun apply(component: JComponent, value: Any?) {
    if (!checkTypes(component, value)) {
      return
    }
    setProperty(component, value)
  }
}

class Properties {
  private val map = HashMap<StyleProperty, Any?>()
  var background: Color?
    set(value) = setValue(StyleProperty.BACKGROUND, value)
    get() = getValue(StyleProperty.BACKGROUND) as Color
  var isOpaque: Boolean?
    set(value) = setValue(StyleProperty.OPAQUE, value)
    get() = getValue(StyleProperty.OPAQUE) as Boolean
  var foreground: Color?
    set(value) = setValue(StyleProperty.FOREGROUND, value)
    get() = getValue(StyleProperty.FOREGROUND) as Color
  var border: Border?
    set(value) = setValue(StyleProperty.BORDER, value)
    get() = getValue(StyleProperty.BORDER) as Border
  var icon: Icon?
    set(value) = setValue(StyleProperty.ICON, value)
    get() = getValue(StyleProperty.ICON) as Icon
  var margin: Insets?
    set(value) = setValue(StyleProperty.MARGIN, value)
    get() = getValue(StyleProperty.MARGIN) as Insets

  fun setValue(prop: StyleProperty, value: Any?) {
    map[prop] = value
  }

  fun getValue(prop: StyleProperty): Any? = map[prop]
  fun clone(): Properties {
    val new = Properties()
    for ((k, v) in map) {
      new.setValue(k, v)
    }
    return new
  }

  fun updateBy(source: Properties) {
    for ((k, v) in source.map) {
      setValue(k, v)
    }
  }

  fun <T : JComponent> applyTo(component: T) {
    for ((k, v) in map) {
      k.apply(component, v)
    }
  }

  public class BasicButton : JButton() {
    init {
      setUI(BasicButtonUI())
    }
  }
}