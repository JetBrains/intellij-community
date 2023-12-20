// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.FontUtil
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Property
import java.awt.Font

@Service(Service.Level.APP)
@State(name = "NotRoamableUiSettings",
       category = SettingsCategory.UI,
       exportable = true,
       storages = [(Storage(StoragePathMacros.NON_ROAMABLE_FILE, roamingType = RoamingType.DISABLED))])
class NotRoamableUiSettings : SerializablePersistentStateComponent<NotRoamableUiOptions>(NotRoamableUiOptions()) {
  private var initialConfigurationLoaded = false
  
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
    get() = state.fontFace ?: JBUIScale.getSystemFontDataIfInitialized()?.first
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

  var experimentalSingleStripe: Boolean
    get() = state.experimentalSingleStripe
    set(value) {
      updateState { it.copy(experimentalSingleStripe = value) }
    }

  override fun loadState(state: NotRoamableUiOptions) {
    var fontSize = UISettings.restoreFontSize(state.fontSize, state.fontScale)
    if (fontSize <= 0) {
      fontSize = getDefaultFontSize()
    }
    var ideScale = state.ideScale
    if (ideScale <= 0) {
      ideScale = getDefaultFontSize()
    }

    super.loadState(state.copy(
      fontSize = fontSize,
      ideScale = ideScale,
    ))

    fixFontSettings()
    if (initialConfigurationLoaded) {
      UISettings.getInstance().fireUISettingsChanged()
    }
    initialConfigurationLoaded = true
  }

  override fun noStateLoaded() {
    initialConfigurationLoaded = true
  }

  internal fun fixFontSettings() {
    val state = state

    // 1. Sometimes system font cannot display standard ASCII symbols.
    // If so, we have to find any other suitable font withing "preferred" fonts first.
    if (state.fontFace == null || FontUtil.isValidFont(Font(state.fontFace, Font.PLAIN, 1).deriveFont(state.fontSize))) {
      return
    }

    var fontIsValid = false
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

  internal fun migratePresentationModeIdeScale(presentationModeFontSize: Int) {
    if (presentationModeIdeScale != 0f) {
      return
    }
    presentationModeIdeScale = if (presentationModeFontSize == 24 || fontSize == 0f)
      UISettingsUtils.defaultScale(isPresentation = true)
    else
      presentationModeFontSize.toFloat() / fontSize
  }
}

data class NotRoamableUiOptions(
  @JvmField @OptionTag val ideAAType: AntialiasingType = if (AntialiasingType.canUseSubpixelAAForIDE()) AntialiasingType.SUBPIXEL else AntialiasingType.GREYSCALE,
  @JvmField @OptionTag val editorAAType: AntialiasingType = if (AntialiasingType.canUseSubpixelAAForEditor()) AntialiasingType.SUBPIXEL else AntialiasingType.GREYSCALE,

  @JvmField
  @field:Property
  val fontFace: String? = null,

  @JvmField
  @field:Property
  val fontSize: Float = 0f,

  @JvmField
  @field:Property
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
  @JvmField
  @OptionTag
  val experimentalSingleStripe: Boolean = false,
)

/**
 * Returns the default font size scaled by #defFontScale
 *
 * @return the default scaled font size
 */
internal fun getDefaultFontSize(): Float = JBUIScale.DEF_SYSTEM_FONT_SIZE * UISettings.defFontScale