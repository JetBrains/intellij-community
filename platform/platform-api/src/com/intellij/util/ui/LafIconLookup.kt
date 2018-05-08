// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * @author Konstantin Bulenkov
 */
object LafIconLookup {
  @JvmStatic
  @JvmOverloads
  fun getIcon(name: String, selected: Boolean = false, focused: Boolean = false, enabled: Boolean = true, editable: Boolean = false, pressed: Boolean = false): Icon {
    return findIcon(name, selected = selected, focused = focused, enabled = enabled, editable = editable, pressed = pressed, isThrowErrorIfNotFound = true)
           ?: AllIcons.Actions.Stub
  }

  fun findIcon(name: String, selected: Boolean = false, focused: Boolean = false, enabled: Boolean = true, editable: Boolean = false, pressed: Boolean = false, isThrowErrorIfNotFound: Boolean = false): Icon? {
    var key = name
    if (editable) key += "Editable"
    if (selected) key += "Selected"

    when {
      pressed -> key += "Pressed"
      focused -> key += "Focused"
      !enabled -> key += "Disabled"
    }

    // For Mac blue theme and other LAFs use default directory icons
    val dir = when {
      UIUtil.isUnderDefaultMacTheme() -> if (UIUtil.isGraphite()) "graphite/" else ""
      UIUtil.isUnderWin10LookAndFeel() -> "win10/"
      UIUtil.isUnderDarcula() -> "darcula/"
      UIUtil.isUnderIntelliJLaF() -> "intellij/"
      else -> ""
    }

    key = dir + key
    return IconLoader.findIcon("/com/intellij/ide/ui/laf/icons/$key.png", LafIconLookup::class.java, true, isThrowErrorIfNotFound)
  }

  @JvmStatic
  fun getDisabledIcon(name: String) = getIcon(name, enabled = false)

  @JvmStatic
  fun getSelectedIcon(name: String) = getIcon(name, selected = true)
}
