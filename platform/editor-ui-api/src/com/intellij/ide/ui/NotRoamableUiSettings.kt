// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.FontUtil
import com.intellij.util.xmlb.Accessor
import com.intellij.util.xmlb.SerializationFilter
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Property
import java.awt.Font

@Service(Service.Level.APP)
@State(name = "NotRoamableUiSettings", storages = [(Storage(StoragePathMacros.NON_ROAMABLE_FILE))])
class NotRoamableUiSettings : SerializablePersistentStateComponent<NotRoamableUiOptions>(defaultState()) {
  companion object {
    fun getInstance(): NotRoamableUiSettings = ApplicationManager.getApplication().service<NotRoamableUiSettings>()
  }

  var ideScale: Float
    get() = state.ideScale
    set(value) {
      updateState { it.copy(ideScale = value) }
    }

  var ideAAType: AntialiasingType
    get() = state.ideAAType
    set(value) {
      updateState { it.copy(ideAAType = value) }
    }

  var fontFace: String?
    get() = state.fontFace
    set(value) {
      updateState { it.copy(fontFace = value) }
    }

  var fontSize: Float
    get() = state.fontSize
    set(value) {
      updateState { it.copy(fontSize = value) }
    }

  var fontScale: Float
    get() = state.fontScale
    set(value) {
      updateState { it.copy(fontScale = value) }
    }

  var editorAAType: AntialiasingType
    get() = state.editorAAType
    set(value) {
      updateState { it.copy(editorAAType = value) }
    }

  var presentationModeIdeScale: Float
    get() = state.presentationModeIdeScale
    set(value) {
      updateState { it.copy(presentationModeIdeScale = value) }
    }

  var overrideLafFonts: Boolean
    get() = state.overrideLafFonts
    set(value) {
      updateState { it.copy(overrideLafFonts = value) }
    }

  override fun loadState(state: NotRoamableUiOptions) {
    var fontSize = UISettings.restoreFontSize(state.fontSize, state.fontScale)
    if (fontSize <= 0) {
      fontSize = UISettingsState.defFontSize
    }
    var ideScale = state.ideScale
    if (ideScale <= 0) {
      ideScale = UISettingsState.defFontSize
    }

    super.loadState(state.copy(
      fontSize = fontSize,
      ideScale = ideScale,
    ))

    fixFontSettings()
  }

  internal fun migratePresentationModeFontSize(presentationModeFontSize: Int) {
    if (state.presentationModeIdeScale != 0f) {
      return
    }

    if (presentationModeFontSize == 24) {
      updateState {
        it.copy(presentationModeIdeScale = UISettingsUtils.defaultScale(true))
      }
    }
    else {
      updateState {
        it.copy(presentationModeIdeScale = presentationModeFontSize.toFloat() / it.fontSize)
      }
    }
  }

  internal fun migrateOverrideLafFonts(overrideLafFonts: Boolean) {
    if (state.overrideLafFontsWasMigrated) {
      return
    }

    updateState {
      it.copy(overrideLafFontsWasMigrated = true, overrideLafFonts = overrideLafFonts)
    }
  }

  internal fun fixFontSettings() {
    val state = state

    // 1. Sometimes system font cannot display standard ASCII symbols.
    // If so, we have to find any other suitable font withing "preferred" fonts first.
    var fontIsValid = FontUtil.isValidFont(Font(state.fontFace, Font.PLAIN, 1).deriveFont(state.fontSize))
    if (!fontIsValid) {
      for (preferredFont in arrayOf("dialog", "Arial", "Tahoma")) {
        if (FontUtil.isValidFont(Font(preferredFont, Font.PLAIN, 1).deriveFont(state.fontSize))) {
          updateState { it.copy(fontFace = preferredFont) }
          fontIsValid = true
          break
        }
      }

      // 2. If all preferred fonts are not valid in the current environment,
      // we have to find the first valid font (if any)
      if (!fontIsValid) {
        val fontNames = FontUtil.getValidFontNames(false)
        if (fontNames.isNotEmpty()) {
          updateState { it.copy(fontFace = fontNames[0]) }
        }
      }
    }
  }
}

private fun defaultState(): NotRoamableUiOptions {
  val fontData = JBUIScale.getSystemFontData(null)
  return NotRoamableUiOptions(
    fontFace = fontData.first,
    fontSize = fontData.second.toFloat(),
  )
}

data class NotRoamableUiOptions(
  @JvmField @OptionTag val ideAAType: AntialiasingType = if (AntialiasingType.canUseSubpixelAAForIDE()) AntialiasingType.SUBPIXEL else AntialiasingType.GREYSCALE,
  @JvmField @OptionTag val editorAAType: AntialiasingType = if (AntialiasingType.canUseSubpixelAAForEditor()) AntialiasingType.SUBPIXEL else AntialiasingType.GREYSCALE,

  @JvmField
  @field:Property(filter = FontFilter::class)
  val fontFace: String? = null,

  @JvmField
  @field:Property(filter = FontFilter::class)
  val fontSize: Float = 0f,

  @JvmField
  @field:Property(filter = FontFilter::class)
  val fontScale: Float = UISettings.defFontScale,

  @JvmField
  @OptionTag
  @field:ReportValue
  val ideScale: Float = 1f,

  @JvmField
  @OptionTag
  @field:ReportValue
  val presentationModeIdeScale: Float = 0f,

  @JvmField
  @OptionTag
  val overrideLafFonts: Boolean = false,
  @JvmField
  @OptionTag
  val overrideLafFontsWasMigrated: Boolean = false,
)

private class FontFilter : SerializationFilter {
  override fun accepts(accessor: Accessor, bean: Any): Boolean {
    val settings = bean as NotRoamableUiOptions
    val fontData = JBUIScale.getSystemFontData(null)
    if ("fontFace" == accessor.name) {
      return fontData.first != settings.fontFace
    }
    // fontSize/fontScale should either be stored in a pair or not stored at all,
    // otherwise the fontSize restore logic gets broken (see loadState)
    return !(fontData.second.toFloat() == settings.fontSize && 1f == settings.fontScale)
  }
}