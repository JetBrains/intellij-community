// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf

import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.plaf.UIResource

open class MenuArrowIcon(val icon: () -> Icon, val selectedIcon: () -> Icon, val disabledIcon: () -> Icon) : Icon, UIResource {
  override fun paintIcon(c: Component, g: Graphics, x: Int, y: Int) {
    if (c is JMenuItem) {
      if (!c.model.isEnabled) {
        disabledIcon().paintIcon(c, g, x, y)
      }
      else if (c.model.isArmed || (c is JMenu && c.model.isSelected)) {
        selectedIcon().paintIcon(c, g, x, y)
      }
      else {
        icon().paintIcon(c, g, x, y)
      }
    }
  }

  override fun getIconWidth(): Int = icon().iconWidth

  override fun getIconHeight(): Int = icon().iconHeight
}
