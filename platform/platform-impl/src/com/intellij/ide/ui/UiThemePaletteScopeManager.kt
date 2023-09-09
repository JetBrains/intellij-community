// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.InsecureHashBuilder

internal class UiThemePaletteScope {
  val newPalette: MutableMap<String, String> = HashMap()
  // 0-255
  val alphas: MutableMap<String, Int> = HashMap()
  private var hash: LongArray? = null

  fun digest(): LongArray {
    hash?.let {
      return it
    }

    // order is significant - use TreeMap
    hash = InsecureHashBuilder()
      .stringMap(newPalette)
      .stringIntMap(alphas)
      .build()
    return hash!!
  }
}

internal class UiThemePaletteScopeManager {
  private val ui = UiThemePaletteScope()
  private val checkBoxes = UiThemePaletteScope()
  private val radioButtons = UiThemePaletteScope()
  private val trees = UiThemePaletteScope()

  fun getScope(colorKey: String): UiThemePaletteScope? {
    return when {
      colorKey.startsWith("Checkbox.") -> checkBoxes
      colorKey.startsWith("Radio.") -> radioButtons
      colorKey.startsWith("Tree.iconColor") -> trees
      colorKey.startsWith("Objects.") -> ui
      colorKey.startsWith("Actions.") -> ui
      colorKey.startsWith('#') -> ui
      else -> {
        thisLogger().warn("No color scope defined for key: $colorKey")
        null
      }
    }
  }

  fun getScopeByPath(path: String?): UiThemePaletteScope? {
    if (path != null && (path.contains("com/intellij/ide/ui/laf/icons/") || path.contains("/com/intellij/ide/ui/laf/icons/"))) {
      val file = path.substring(path.lastIndexOf('/') + 1)
      return when {
        file == "treeCollapsed.svg" || file == "treeExpanded.svg" -> trees
        file.startsWith("check") -> checkBoxes
        // same set of colors as for checkboxes
        file.startsWith("radio") -> checkBoxes
        else -> null
      }
    }
    return ui
  }
}