// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * @author Konstantin Bulenkov
 */
private const val ICONS_DIR_PREFIX = "com/intellij/ide/ui/laf/icons/"

private val defaultDirProver = DirProvider()

open class DirProvider {
  open val defaultExtension: String
    get() = "png"

  open fun dir(): String = ICONS_DIR_PREFIX + if (StartupUiUtil.isUnderDarcula()) "darcula/" else "intellij/"
}

object LafIconLookup {
  @JvmStatic
  @JvmOverloads
  fun getIcon(name: String,
              selected: Boolean = false,
              focused: Boolean = false,
              enabled: Boolean = true,
              editable: Boolean = false,
              pressed: Boolean = false): Icon {

    return findIcon(name,
                    selected = selected,
                    focused = focused,
                    enabled = enabled,
                    editable = editable,
                    pressed = pressed,
                    dirProvider = DirProvider())
           ?: AllIcons.Actions.Stub
  }

  fun findIcon(name: String,
               selected: Boolean = false,
               focused: Boolean = false,
               enabled: Boolean = true,
               editable: Boolean = false,
               pressed: Boolean = false,
               dirProvider: DirProvider = defaultDirProver): Icon? {
    var key = name
    if (editable) {
      key += "Editable"
    }
    if (selected) {
      key += "Selected"
    }

    when {
      pressed -> key += "Pressed"
      focused -> key += "Focused"
      !enabled -> key += "Disabled"
    }

    // for Mac blue theme and other LAFs use default directory icons
    val providerClass = dirProvider.javaClass
    val classLoader = providerClass.classLoader
    val dir = dirProvider.dir()
    val path = if (dir.startsWith(ICONS_DIR_PREFIX)) {
      // optimization - all icons are SVG
      "$dir$key.svg"
    }
    else {
      "$dir$key.${dirProvider.defaultExtension}"
    }

    return IconLoader.findIcon(path, classLoader)
  }

  @JvmStatic
  fun getDisabledIcon(name: String): Icon = getIcon(name, enabled = false)

  @JvmStatic
  fun getSelectedIcon(name: String): Icon = findIcon(name, selected = true) ?: getIcon(name)
}