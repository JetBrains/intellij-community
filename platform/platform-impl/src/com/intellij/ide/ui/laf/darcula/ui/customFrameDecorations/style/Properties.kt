// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui.customFrameDecorations.style

import java.awt.Color
import java.awt.Insets
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.border.Border

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
    new.map +=map
    return new
  }

  fun updateBy(source: Properties): Properties {
    map += source.map
    return this
  }

  fun <T : JComponent> applyTo(component: T) {
    for ((k, v) in map) {
      k.apply(component, v)
    }
  }
}