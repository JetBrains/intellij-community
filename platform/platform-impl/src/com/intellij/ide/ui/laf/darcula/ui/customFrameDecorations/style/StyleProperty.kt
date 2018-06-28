// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui.customFrameDecorations.style

import com.intellij.openapi.diagnostic.Logger
import java.awt.Color
import java.awt.Insets
import javax.swing.AbstractButton
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.border.Border
import javax.swing.plaf.basic.BasicButtonUI

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
  private fun checkTypes(component: JComponent, value: Any?): Boolean {
    if (!componentType.isInstance(component)) {
      log.warn(
        javaClass.canonicalName + " Incorrect class type: " + component.javaClass.canonicalName + " instead of " + componentType.canonicalName)
      return false
    }
    if (valueType == Boolean::class.java) {
      return (value == true || value == false)
    }
    if (!(value == null || valueType.isInstance(value))) {
      log.warn(
        javaClass.canonicalName + " Incorrect value type: " + value.javaClass.canonicalName + " instead of " + valueType.canonicalName)
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

  class BasicButton : JButton() {
    init {
      setUI(BasicButtonUI())
    }
  }
}