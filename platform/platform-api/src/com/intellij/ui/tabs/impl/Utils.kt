// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.tabs.impl

import com.intellij.openapi.rd.childAtMouse
import com.jetbrains.rd.util.reactive.map
import java.awt.Component
import java.awt.KeyboardFocusManager
import java.awt.Window
import javax.swing.JComponent

class Utils {
  companion object {
    fun isFocusOwner(c: Component): Boolean {
      val focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner

      if (focusOwner == null) return false
      return Utils.isDescendant(c, focusOwner)
    }

    fun isChild(component: Component?, parent: Component?): Boolean {
      component ?: return false
      parent ?: return false

      return if (parent === component) true else isChild(component.getParent(), parent)
    }

    fun isDescendant(component: Component, parent: Component): Boolean {
      // verify parent is a descendant of component
      var temp: Component? = parent
      while (temp != null) {
        if (temp === component) {
          return true
        }
        temp = if (temp is Window) null else temp.parent
      }

      return false
    }
  }
}

fun JComponent.getTabLabelUnderMouse() = this@getTabLabelUnderMouse.childAtMouse().map { if (it is TabLabel) it else null }
