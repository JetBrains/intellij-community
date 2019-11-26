// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * @author Konstantin Bulenkov
 */
object LafIconLookup {
  private const val ICONS_DIR_PREFIX = "/com/intellij/ide/ui/laf/icons/"

  @JvmStatic
  @JvmOverloads
  fun getIcon(name: String,
              selected: Boolean = false,
              focused: Boolean = false,
              enabled: Boolean = true,
              editable: Boolean = false,
              pressed: Boolean = false,
              dirProvider: () -> String = { defaultDirProvider() }): Icon {

    return findIcon(name,
                    selected = selected,
                    focused = focused,
                    enabled = enabled,
                    editable = editable,
                    pressed = pressed,
                    isThrowErrorIfNotFound = true,
                    dirProvider = dirProvider)
           ?: AllIcons.Actions.Stub
  }

  fun findIcon(name: String,
               selected: Boolean = false,
               focused: Boolean = false,
               enabled: Boolean = true,
               editable: Boolean = false,
               pressed: Boolean = false,
               isThrowErrorIfNotFound: Boolean = false,
               dirProvider: () -> String = { defaultDirProvider() }): Icon? {
    var key = name
    if (editable) key += "Editable"
    if (selected) key += "Selected"

    when {
      pressed -> key += "Pressed"
      focused -> key += "Focused"
      !enabled -> key += "Disabled"
    }

    // For Mac blue theme and other LAFs use default directory icons
    return IconLoader.findLafIcon(dirProvider() + key, LafIconLookup::class.java, isThrowErrorIfNotFound)
  }

  private fun defaultDirProvider() : String = ICONS_DIR_PREFIX + when {
    UIUtil.isUnderDarcula() -> "darcula/"
    UIUtil.isUnderIntelliJLaF() -> "intellij/"
    else -> ""
  }

  @JvmStatic
  fun getDisabledIcon(name: String): Icon = getIcon(name, enabled = false)

  @JvmStatic
  fun getSelectedIcon(name: String): Icon = findIcon(name, selected = true) ?: getIcon(name)
}
