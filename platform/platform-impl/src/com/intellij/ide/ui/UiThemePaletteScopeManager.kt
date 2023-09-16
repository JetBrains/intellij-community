// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.ide.ui

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.ui.svg.SvgAttributePatcher
import com.intellij.ui.svg.newSvgPatcher
import com.intellij.util.InsecureHashBuilder
import com.intellij.util.concurrency.SynchronizedClearableLazy
import java.util.function.Supplier

internal class UiThemePaletteScope {
  @JvmField
  val newPalette: MutableMap<String, String> = HashMap()

  // 0-255
  @JvmField
  val alphas: MutableMap<String, Int> = HashMap()

  @JvmField
  val svgColorIconPatcher: Supplier<SvgAttributePatcher?> = SynchronizedClearableLazy {
    if (newPalette.isEmpty()) {
      return@SynchronizedClearableLazy null
    }

    val hash = updateHash(InsecureHashBuilder()).build()
    newSvgPatcher(digest = hash, newPalette = newPalette) { alphas.get(it) }
  }

  fun updateHash(insecureHashBuilder: InsecureHashBuilder): InsecureHashBuilder {
    insecureHashBuilder
      .stringMap(newPalette)
      .stringIntMap(alphas)
    return insecureHashBuilder
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