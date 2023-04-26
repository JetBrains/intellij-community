// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.openapi.components.*
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.FontUtil
import com.intellij.util.xmlb.Accessor
import com.intellij.util.xmlb.SerializationFilter
import com.intellij.util.xmlb.annotations.Property
import java.awt.Font

@State(name = "NotRoamableUiSettings", storages = [(Storage(StoragePathMacros.NON_ROAMABLE_FILE))])
class NotRoamableUiSettings : PersistentStateComponent<NotRoamableUiOptions> {
  private var state = NotRoamableUiOptions()

  override fun getState() = state

  override fun loadState(state: NotRoamableUiOptions) {
    this.state = state

    state.fontSize = UISettings.restoreFontSize(state.fontSize, state.fontScale)
    state.fontScale = UISettings.defFontScale
    fixFontSettings()
  }

  internal fun migratePresentationModeFontSize(presentationModeFontSize: Int) {
    if (state.presentationModeIdeScale != 0f) return
    if (presentationModeFontSize == 24) state.presentationModeIdeScale = UISettingsUtils.defaultScale(true)
    else state.presentationModeIdeScale = presentationModeFontSize.toFloat() / state.fontSize
  }

  internal fun migrateOverrideLafFonts(overrideLafFonts: Boolean) {
    if (state.overrideLafFontsWasMigrated) return
    state.overrideLafFontsWasMigrated = true
    state.overrideLafFonts = overrideLafFonts
  }

  internal fun fixFontSettings() {
    val state = state

    // 1. Sometimes system font cannot display standard ASCII symbols. If so we have
    // find any other suitable font withing "preferred" fonts first.
    var fontIsValid = FontUtil.isValidFont(Font(state.fontFace, Font.PLAIN, 1).deriveFont(state.fontSize))
    if (!fontIsValid) {
      for (preferredFont in arrayOf("dialog", "Arial", "Tahoma")) {
        if (FontUtil.isValidFont(Font(preferredFont, Font.PLAIN, 1).deriveFont(state.fontSize))) {
          state.fontFace = preferredFont
          fontIsValid = true
          break
        }
      }

      // 2. If all preferred fonts are not valid in current environment
      // we have to find first valid font (if any)
      if (!fontIsValid) {
        val fontNames = FontUtil.getValidFontNames(false)
        if (fontNames.isNotEmpty()) {
          state.fontFace = fontNames[0]
        }
      }
    }
  }
}

class NotRoamableUiOptions : BaseState() {
  var ideAAType by enum(
    if (AntialiasingType.canUseSubpixelAAForIDE())
      AntialiasingType.SUBPIXEL else AntialiasingType.GREYSCALE)

  var editorAAType by enum(
    if (AntialiasingType.canUseSubpixelAAForEditor())
      AntialiasingType.SUBPIXEL else AntialiasingType.GREYSCALE)

  @get:Property(filter = FontFilter::class)
  var fontFace by string()

  @get:Property(filter = FontFilter::class)
  var fontSize by property(0f)

  @get:Property(filter = FontFilter::class)
  var fontScale by property(0f)

  @get:ReportValue
  var ideScale by property(1f)

  @get:ReportValue
  var presentationModeIdeScale by property(0f)

  var overrideLafFonts by property(false)
  var overrideLafFontsWasMigrated by property(false)

  init {
    val fontData = JBUIScale.getSystemFontData(null)
    fontFace = fontData.first
    fontSize = fontData.second.toFloat()
    fontScale = UISettings.defFontScale
    ideScale = UISettingsUtils.defaultScale(false)
  }
}

private class FontFilter : SerializationFilter {
  override fun accepts(accessor: Accessor, bean: Any): Boolean {
    val settings = bean as NotRoamableUiOptions
    val fontData = JBUIScale.getSystemFontData(null)
    if ("fontFace" == accessor.name) {
      return fontData.first != settings.fontFace
    }
    // fontSize/fontScale should either be stored in pair or not stored at all
    // otherwise the fontSize restore logic gets broken (see loadState)
    return !(fontData.second.toFloat() == settings.fontSize && 1f == settings.fontScale)
  }
}