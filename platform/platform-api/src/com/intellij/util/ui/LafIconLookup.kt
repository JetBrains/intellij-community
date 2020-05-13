// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * @author Konstantin Bulenkov
 */
private const val ICONS_DIR_PREFIX = "/com/intellij/ide/ui/laf/icons/"

open class DirProvider {
  open fun dir() : String = ICONS_DIR_PREFIX + if (UIUtil.isUnderDarcula()) "darcula/" else "intellij/"
}

object LafIconLookup {
  @JvmStatic
  @JvmOverloads
  fun getIcon(name: String,
              selected: Boolean = false,
              focused: Boolean = false,
              enabled: Boolean = true,
              editable: Boolean = false,
              pressed: Boolean = false) : Icon {

    return findIcon(name,
                    selected = selected,
                    focused = focused,
                    enabled = enabled,
                    editable = editable,
                    pressed = pressed,
                    isThrowErrorIfNotFound = true,
                    dirProvider = DirProvider())
           ?: AllIcons.Actions.Stub
  }

  fun findIcon(name: String,
               selected: Boolean = false,
               focused: Boolean = false,
               enabled: Boolean = true,
               editable: Boolean = false,
               pressed: Boolean = false,
               isThrowErrorIfNotFound: Boolean = false,
               dirProvider: DirProvider = DirProvider()): Icon? {
    var key = name
    if (editable) key += "Editable"
    if (selected) key += "Selected"

    when {
      pressed -> key += "Pressed"
      focused -> key += "Focused"
      !enabled -> key += "Disabled"
    }

    // For Mac blue theme and other LAFs use default directory icons
    return IconLoader.findLafIcon(dirProvider.dir() + key, dirProvider.javaClass, isThrowErrorIfNotFound)
  }

  @JvmStatic
  fun getDisabledIcon(name: String): Icon = getIcon(name, enabled = false)

  @JvmStatic
  fun getSelectedIcon(name: String): Icon = findIcon(name, selected = true) ?: getIcon(name)
}
