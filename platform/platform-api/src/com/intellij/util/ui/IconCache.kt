// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui

import com.intellij.openapi.util.IconLoader
import org.jetbrains.annotations.TestOnly

import javax.swing.*
import java.util.HashMap

/**
 * @author Konstantin Bulenkov
 */
object IconCache {
  private val cache = HashMap<String, Icon>()

  fun getIcon(name: String, editable: Boolean, selected: Boolean, focused: Boolean, enabled: Boolean, pressed: Boolean): Icon? {
    return getIcon(name, editable, selected, focused, enabled, pressed, true)
  }

  private fun getIcon(name: String,
                      editable: Boolean,
                      selected: Boolean,
                      focused: Boolean,
                      enabled: Boolean,
                      pressed: Boolean,
                      findIfNotInCache: Boolean): Icon? {
    var key = name
    if (editable) key += "Editable"
    if (selected) key += "Selected"

    if (pressed)
      key += "Pressed"
    else if (focused)
      key += "Focused"
    else if (!enabled) key += "Disabled"

    var dir = ""

    // For Mac blue theme and other LAFs use default directory icons
    if (UIUtil.isUnderDefaultMacTheme())
      dir = if (UIUtil.isGraphite()) "graphite/" else ""
    else if (UIUtil.isUnderWin10LookAndFeel())
      dir = "win10/"
    else if (UIUtil.isUnderDarcula())
      dir = "darcula/"
    else if (UIUtil.isUnderIntelliJLaF()) dir = "intellij/"

    key = dir + key

    var icon: Icon? = cache[key]
    if (icon == null && findIfNotInCache) {
      icon = IconLoader.findIcon("/com/intellij/ide/ui/laf/icons/$key.png", IconCache::class.java, true)
      cache[key] = icon
    }
    return icon
  }

  fun getIcon(name: String, editable: Boolean, selected: Boolean, focused: Boolean, enabled: Boolean): Icon? {
    return getIcon(name, editable, selected, focused, enabled, false, true)
  }

  fun getIcon(name: String, selected: Boolean, focused: Boolean, enabled: Boolean): Icon? {
    return getIcon(name, false, selected, focused, enabled)
  }

  fun getIcon(name: String, selected: Boolean, focused: Boolean): Icon? {
    return getIcon(name, false, selected, focused, true)
  }

  fun getIcon(name: String): Icon? {
    return getIcon(name, false, false, false, true, false, true)
  }

  // this method will be not required when this class will be converted to Kotlin (since Kotlin supports named parameters)
  @TestOnly
  fun getCachedIcon(name: String, selected: Boolean): Icon? {
    return getIcon(name, false, selected, false, true, false, false)
  }
}
