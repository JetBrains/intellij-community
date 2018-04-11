// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui

import com.intellij.openapi.util.IconLoader
import gnu.trove.THashMap
import org.jetbrains.annotations.TestOnly
import javax.swing.Icon

/**
 * @author Konstantin Bulenkov
 */
object IconCache {
  private val cache = THashMap<String, Icon>()

  @JvmStatic
  @JvmOverloads
  fun getIcon(name: String, editable: Boolean, selected: Boolean, focused: Boolean, enabled: Boolean = true, pressed: Boolean = false, findIfNotInCache: Boolean = true): Icon? {
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

    var icon: Icon? = cache.get(key)
    if (icon == null && findIfNotInCache) {
      icon = IconLoader.findIcon("/com/intellij/ide/ui/laf/icons/$key.png", IconCache::class.java, true)
      cache.put(key, icon)
    }
    return icon
  }

  @JvmStatic
  fun getDisabledIcon(name: String) = getIcon(name, editable = false, selected = false, focused = false, enabled = false)!!

  @JvmStatic
  fun getSelectedIcon(name: String) = getIcon(name, editable = false, selected = true, focused = false)!!

  @JvmStatic
  fun getIcon(name: String, selected: Boolean, focused: Boolean): Icon? {
    return getIcon(name, false, selected, focused)
  }

  @JvmStatic
  fun getIcon(name: String): Icon? {
    return getIcon(name, false, false, false)
  }

  // this method will be not required when this class will be converted to Kotlin (since Kotlin supports named parameters)
  @TestOnly
  fun getCachedIcon(name: String, selected: Boolean): Icon? {
    return getIcon(name, false, selected, false, findIfNotInCache = false)
  }
}
