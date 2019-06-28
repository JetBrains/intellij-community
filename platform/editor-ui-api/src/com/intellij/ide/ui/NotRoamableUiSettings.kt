// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui

import com.intellij.openapi.components.*
import com.intellij.openapi.util.Pair
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.UIUtil
import com.intellij.util.xmlb.Accessor
import com.intellij.util.xmlb.SerializationFilter
import com.intellij.util.xmlb.annotations.Property
import java.awt.Font

@State(name = "NotRoamableUiSettings", storages = [(Storage(StoragePathMacros.NON_ROAMABLE_FILE))], reportStatistic = true)
class NotRoamableUiSettings : PersistentStateComponent<NotRoamableUiOptions> {
  private var state = NotRoamableUiOptions()

  override fun getState() = state

  override fun loadState(state: NotRoamableUiOptions) {
    this.state = state

    state.fontSize = UISettings.restoreFontSize(state.fontSize, state.fontScale)
    state.fontScale = UISettings.defFontScale
    fixFontSettings()
  }

  internal fun fixFontSettings() {
    val state = state

    // 1. Sometimes system font cannot display standard ASCII symbols. If so we have
    // find any other suitable font withing "preferred" fonts first.
    var fontIsValid = UIUtil.isValidFont(Font(state.fontFace, Font.PLAIN, state.fontSize))
    if (!fontIsValid) {
      for (preferredFont in arrayOf("dialog", "Arial", "Tahoma")) {
        if (UIUtil.isValidFont(Font(preferredFont, Font.PLAIN, state.fontSize))) {
          state.fontFace = preferredFont
          fontIsValid = true
          break
        }
      }

      // 2. If all preferred fonts are not valid in current environment
      // we have to find first valid font (if any)
      if (!fontIsValid) {
        val fontNames = UIUtil.getValidFontNames(false)
        if (fontNames.isNotEmpty()) {
          state.fontFace = fontNames[0]
        }
      }
    }
  }
}

class NotRoamableUiOptions : BaseState() {
  var ideAAType by enum(AntialiasingType.SUBPIXEL)

  var editorAAType by enum(AntialiasingType.SUBPIXEL)

  @get:Property(filter = FontFilter::class)
  var fontFace by string()

  @get:Property(filter = FontFilter::class)
  var fontSize by property(0)

  @get:Property(filter = FontFilter::class)
  var fontScale by property(0f)

  init {
    val fontData = systemFontFaceAndSize
    fontFace = fontData.first
    fontSize = fontData.second
    fontScale = UISettings.defFontScale
  }
}

private class FontFilter : SerializationFilter {
  override fun accepts(accessor: Accessor, bean: Any): Boolean {
    val settings = bean as NotRoamableUiOptions
    val fontData = systemFontFaceAndSize
    if ("fontFace" == accessor.name) {
      return fontData.first != settings.fontFace
    }
    // fontSize/fontScale should either be stored in pair or not stored at all
    // otherwise the fontSize restore logic gets broken (see loadState)
    return !(fontData.second == settings.fontSize && 1f == settings.fontScale)
  }
}

private val systemFontFaceAndSize: Pair<String, Int>
  get() = JBUIScale.getSystemFontData()