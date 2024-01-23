// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.ui.ColorUtil
import com.intellij.ui.svg.*
import com.intellij.util.InsecureHashBuilder
import com.intellij.util.concurrency.SynchronizedClearableLazy
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import java.util.function.Supplier

@ApiStatus.Internal
const val FILL_STROKE_SEPARATOR = '_'

@ApiStatus.Internal
class PaletteKeys(id: String) {

  val fillKey: String
  val strokeKey: String

  init {
    val splitId = id.split(FILL_STROKE_SEPARATOR)
    if (splitId.size == 2) {
      fillKey = splitId[0]
      strokeKey = splitId[1]
    }
    else {
      fillKey = id
      strokeKey = id
    }
  }
}

/**
 * CheckBox scope for new themes, see [NewThemeCheckboxPatcher] for details
 */
internal class UiThemePaletteCheckBoxScope(private val isDarkTheme: Boolean) : UiThemePaletteScope {

  /**
   * Checkbox mapping from ColorPalette of theme, keys are from [paletteNames]
   */
  private val palette = hashMapOf<String, String>()

  // 0-255, keys are from [paletteNames]
  private val alphas = hashMapOf<String, Int>()

  override val svgColorIconPatcher: SvgAttributePatcher?
    get() = lazySvgColorIconPatcher.get()

  private val lazySvgColorIconPatcher: Supplier<SvgAttributePatcher?> = SynchronizedClearableLazy {
    if (palette.isEmpty()) {
      return@SynchronizedClearableLazy null
    }
    else {
      NewThemeCheckboxPatcher(palette, alphas)
    }
  }

  fun registerPalette(colorKey: String, color: Any?) {
    if (!paletteNames.contains(colorKey)) {
      thisLogger().warn("Color key is not supported: $colorKey")
      return
    }

    if (color is Color) {
      val colorHex = "#" + ColorUtil.toHex(color, false)
      palette[colorKey] = colorHex
      alphas[colorKey] = color.alpha
    }
  }

  override fun updateHash(insecureHashBuilder: InsecureHashBuilder) {
    insecureHashBuilder
      .putBoolean(isDarkTheme)
      .putStringMap(palette)
      .putStringIntMap(alphas)
  }
}

/**
 * Every painted element in svg must have id. If only one from fill or stroke attribute is used, then id is used as a key for it.
 * If both fill and stroke are used, then id must be in format `fillKey_strokeKey` (see [FILL_STROKE_SEPARATOR])
 */
private class NewThemeCheckboxPatcher(private val palette: Map<String, String>,
                                      private val alphas: Map<String, Int>) : SvgAttributePatcher {

  override fun patchColors(attributes: MutableMap<String, String>) {
    val paletteKeys = PaletteKeys(attributes[ATTR_ID] ?: return)
    patchAttributes(attributes, ATTR_FILL, ATTR_FILL_OPACITY, palette[paletteKeys.fillKey], alphas[paletteKeys.fillKey])
    patchAttributes(attributes, ATTR_STROKE, ATTR_STROKE_OPACITY, palette[paletteKeys.strokeKey], alphas[paletteKeys.strokeKey])
  }

  private fun patchAttributes(attributes: MutableMap<String, String>, attributeName: String, opacityAttributeName: String,
                              newColor: String?, newOpacity: Int?) {
    if (!attributes.containsKey(attributeName) || newColor == null) {
      return
    }

    attributes[attributeName] = newColor

    if (newOpacity == null || newOpacity == 255) {
      attributes.remove(opacityAttributeName)
    }
    else {
      attributes[opacityAttributeName] = (newOpacity / 255f).toString()
    }
  }
}

private val paletteNames: Set<String> = setOf(
  "Checkbox.Background.Default",
  "Checkbox.Border.Default",
  "Checkbox.Foreground.Selected",
  "Checkbox.Background.Selected",
  "Checkbox.Border.Selected",
  "Checkbox.Focus.Wide",
  "Checkbox.Foreground.Disabled",
  "Checkbox.Background.Disabled",
  "Checkbox.Border.Disabled"
)
