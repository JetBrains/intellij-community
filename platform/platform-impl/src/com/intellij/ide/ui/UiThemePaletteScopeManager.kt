// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.ide.ui

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.ui.ColorUtil
import com.intellij.ui.svg.SvgAttributePatcher
import com.intellij.ui.svg.newSvgPatcher
import com.intellij.util.InsecureHashBuilder
import com.intellij.util.SVGLoader
import com.intellij.util.concurrency.SynchronizedClearableLazy
import java.awt.Color
import java.util.function.Supplier

sealed interface UiThemePaletteScope {

  val svgColorIconPatcher: SvgAttributePatcher?
  fun updateHash(insecureHashBuilder: InsecureHashBuilder)
}

private class UiThemePaletteScopeImpl : UiThemePaletteScope {

  val newPalette: MutableMap<String, String> = HashMap()

  // 0-255
  val alphas: MutableMap<String, Int> = HashMap()

  override val svgColorIconPatcher: SvgAttributePatcher?
    get() = lazySvgColorIconPatcher.get()

  private val lazySvgColorIconPatcher: Supplier<SvgAttributePatcher?> = SynchronizedClearableLazy {
    if (newPalette.isEmpty()) {
      return@SynchronizedClearableLazy null
    }
    else {
      newSvgPatcher(newPalette = newPalette) { alphas.get(it) }
    }
  }

  override fun updateHash(insecureHashBuilder: InsecureHashBuilder) {
    insecureHashBuilder
      .putStringMap(newPalette)
      .putStringIntMap(alphas)
  }
}

internal class UiThemePaletteScopeManager(theme: UIThemeBean) {

  private val isRootThemeExperimental = UITheme.isRootThemeExperimental(theme)

  private val ui = UiThemePaletteScopeImpl()
  private val checkBoxes = UiThemePaletteScopeImpl()
  private val trees = UiThemePaletteScopeImpl()
  private val checkBoxesNewThemes = UiThemePaletteCheckBoxScope(theme.dark)

  fun configureIcons(theme: UIThemeBean,
                     iconMap: Map<String, Any?>): SVGLoader.SvgElementColorPatcherProvider? {
    @Suppress("UNCHECKED_CAST")
    val palette = iconMap.get("ColorPalette") as? Map<String, String> ?: return null
    for (colorKey in palette.keys) {
      var v: Any? = palette.get(colorKey)
      if (v is String) {
        // named
        v = theme.colorMap.map.get(v)
      }

      when (val scope = getScope(colorKey)) {
        is UiThemePaletteScopeImpl -> {
          val key = toColorString(key = colorKey, darkTheme = theme.dark)
          if (v == null) {
            v = parseColorOrNull(key, null)
          }

          val colorFromKey = parseColorOrNull(key, null)
          if (colorFromKey != null && v is Color) {
            val fillTransparency = v.alpha
            val colorHex = "#" + ColorUtil.toHex(v, false)
            scope.newPalette[key] = colorHex
            scope.alphas[colorHex] = fillTransparency
          }
        }

        is UiThemePaletteCheckBoxScope -> {
          scope.registerPalette(colorKey, v)
        }

        null -> {}
      }
    }

    val digest = computeDigest(InsecureHashBuilder()).build()
    return object : SVGLoader.SvgElementColorPatcherProvider {
      override fun digest() = digest

      override fun attributeForPath(path: String): SvgAttributePatcher? {
        return getScopeByPath(path)?.svgColorIconPatcher
      }
    }
  }

  fun computeDigest(builder: InsecureHashBuilder): InsecureHashBuilder {
    // id and version of this class implementation, see ColorPatcherIdGenerator
    builder.putLong(453973057740471735)
      .putBoolean(isRootThemeExperimental)
    ui.updateHash(builder)
    checkBoxes.updateHash(builder)
    trees.updateHash(builder)
    checkBoxesNewThemes.updateHash(builder)
    return builder
  }

  private fun getScope(colorKey: String): UiThemePaletteScope? {
    return when {
      colorKey.startsWith("Checkbox.") -> if (isRootThemeExperimental) checkBoxesNewThemes else checkBoxes
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
    if (path != null) {
      if (isRootThemeExperimental && path.contains("themes/expUI/icons/dark/")) {
        return checkBoxesNewThemes
      }

      if (path.contains("com/intellij/ide/ui/laf/icons/")) {
        val file = path.substring(path.lastIndexOf('/') + 1)
        return when {
          file == "treeCollapsed.svg" || file == "treeExpanded.svg" -> trees
          file.startsWith("check") -> checkBoxes
          // same set of colors as for checkboxes
          file.startsWith("radio") -> checkBoxes
          else -> null
        }
      }
    }
    return ui
  }
}
