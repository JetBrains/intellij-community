// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.editor.fonts

import com.intellij.application.options.colors.AbstractFontOptionsPanel
import com.intellij.application.options.colors.ColorAndFontOptions
import com.intellij.application.options.colors.ColorAndFontSettingsListener
import com.intellij.ide.DataManager
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.FontPreferences
import com.intellij.openapi.editor.colors.ModifiableFontPreferences
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl
import com.intellij.openapi.editor.impl.FontFamilyService
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.OptionsBundle
import com.intellij.openapi.options.colors.pages.GeneralColorsPage
import com.intellij.openapi.options.ex.Settings
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.AbstractFontCombo
import com.intellij.ui.components.ActionLink
import com.intellij.ui.layout.*
import com.intellij.util.ObjectUtils
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Dimension
import java.util.function.Consumer
import javax.swing.JComponent

class AppEditorFontOptionsPanel(private val scheme: EditorColorsScheme) : AbstractFontOptionsPanel() {

  private val defaultPreferences = FontPreferencesImpl()

  private lateinit var restoreDefaults: ActionLink
  private var regularWeightCombo: FontWeightCombo? = null
  private var boldWeightCombo: FontWeightCombo? = null

  init {
    addListener(object : ColorAndFontSettingsListener.Abstract() {
      override fun fontChanged() {
        updateFontPreferences()
      }
    })

    AppEditorFontOptions.initDefaults(defaultPreferences)
    updateOptionsList()
  }

  override fun isReadOnly(): Boolean {
    return false
  }

  override fun isDelegating(): Boolean {
    return false
  }

  override fun getFontPreferences(): FontPreferences {
    return scheme.fontPreferences
  }

  override fun setFontSize(fontSize: Int) {
    scheme.editorFontSize = fontSize
  }

  override fun getLineSpacing(): Float {
    return scheme.lineSpacing
  }

  override fun setCurrentLineSpacing(lineSpacing: Float) {
    scheme.lineSpacing = lineSpacing
  }

  override fun createPrimaryFontCombo(): AbstractFontCombo<*>? {
    return if (isAdvancedFontFamiliesUI()) {
      FontFamilyCombo(true)
    }
    else {
      super.createPrimaryFontCombo()
    }
  }

  override fun createSecondaryFontCombo(): AbstractFontCombo<*>? {
    return if (isAdvancedFontFamiliesUI()) {
      FontFamilyCombo(false)
    }
    else {
      super.createSecondaryFontCombo()
    }
  }

  fun updateOnChangedFont() {
    updateOptionsList()
    fireFontChanged()
  }

  private fun restoreDefaults() {
    AppEditorFontOptions.initDefaults(fontPreferences as ModifiableFontPreferences)
    updateOnChangedFont()
  }

  override fun createControls(): JComponent {
    return panel {
      row(primaryLabel) {
        component(primaryCombo)
      }

      row(sizeLabel) {
        component(editorFontSizeField)
        component(lineSpacingLabel).withLargeLeftGap()
        component(lineSpacingField)
      }

      fullRow {
        component(enableLigaturesCheckbox)
        component(enableLigaturesHintLabel).withLeftGap()
      }

      row {
        hyperlink(ApplicationBundle.message("comment.use.ligatures.with.reader.mode"),
                  style = UIUtil.ComponentStyle.SMALL,
                  color = UIUtil.getLabelFontColor(UIUtil.FontColor.BRIGHTER)) {
          goToReaderMode()
        }
      }

      row {
        restoreDefaults = ActionLink(ApplicationBundle.message("settings.editor.font.restored.defaults")) {
          restoreDefaults()
        }

        component(restoreDefaults)
      }

      row {
        component(createTypographySettings())
      }
    }.withBorder(JBUI.Borders.empty(BASE_INSET))
  }

  fun updateFontPreferences() {
    restoreDefaults.isEnabled = defaultPreferences != fontPreferences

    regularWeightCombo?.apply { update(fontPreferences) }
    boldWeightCombo?.apply { update(fontPreferences) }
  }

  private fun createTypographySettings(): DialogPanel {
    return panel {
      hideableRow(ApplicationBundle.message("settings.editor.font.typography.settings"),
                  incrementsIndent = false) {
        if (isAdvancedFontFamiliesUI()) {
          row(ApplicationBundle.message("settings.editor.font.main.weight")) {
            val component = createRegularWeightCombo()
            regularWeightCombo = component
            component(component)
          }

          row(ApplicationBundle.message("settings.editor.font.bold.weight")) {
            val component = createBoldWeightCombo()
            boldWeightCombo = component
            component(component)
          }

          row("") {
            hyperlink(ApplicationBundle.message("settings.editor.font.bold.weight.hint"),
                      style = UIUtil.ComponentStyle.SMALL,
                      color = UIUtil.getContextHelpForeground()) {
              navigateToColorSchemeTextSettings()
            }
          } // .largeGapAfter() doesn't work now
        }

        row {
          val secondaryFont = label(ApplicationBundle.message("secondary.font"))
          setSecondaryFontLabel(secondaryFont.component)
          component(secondaryCombo)
        }

        row("") {
          val description = label(ApplicationBundle.message("label.fallback.fonts.list.description"),
                                  style = UIUtil.ComponentStyle.SMALL)
          description.component.foreground = UIUtil.getContextHelpForeground()
        }
      }
    }
  }

  private fun navigateToColorSchemeTextSettings() {
    var defaultTextOption = OptionsBundle.message("options.general.attribute.descriptor.default.text")
    val separator = "//"
    val separatorPos = defaultTextOption.lastIndexOf(separator)
    if (separatorPos > 0) {
      defaultTextOption = defaultTextOption.substring(separatorPos + separator.length)
    }
    val allSettings = Settings.KEY.getData(DataManager.getInstance().getDataContext(this))
    if (allSettings != null) {
      val colorSchemeConfigurable = allSettings.find(ColorAndFontOptions.ID)
      if (colorSchemeConfigurable is ColorAndFontOptions) {
        val generalSettings: Configurable? = colorSchemeConfigurable.findSubConfigurable(GeneralColorsPage.getDisplayNameText())
        if (generalSettings != null) {
          allSettings.select(generalSettings, defaultTextOption)
        }
      }
    }
  }

  private fun createRegularWeightCombo(): FontWeightCombo {
    val result = RegularFontWeightCombo()
    fixComboWidth(result)

    result.addActionListener {
      changeFontPreferences { preferences: ModifiableFontPreferences ->
        val newSubFamily = result.selectedSubFamily
        if (preferences.regularSubFamily != newSubFamily) {
          preferences.boldSubFamily = null // Reset bold subfamily for a different regular
        }
        preferences.regularSubFamily = newSubFamily
      }
    }

    return result
  }

  private fun createBoldWeightCombo(): FontWeightCombo {
    val result = BoldFontWeightCombo()
    fixComboWidth(result)

    result.addActionListener {
      changeFontPreferences { preferences: ModifiableFontPreferences ->
        preferences.boldSubFamily = result.selectedSubFamily
      }
    }

    return result
  }

  private fun changeFontPreferences(consumer: Consumer<ModifiableFontPreferences>) {
    val preferences = fontPreferences
    assert(preferences is ModifiableFontPreferences)

    consumer.accept(preferences as ModifiableFontPreferences)
    fireFontChanged()
  }

  private class RegularFontWeightCombo : FontWeightCombo(false) {

    public override fun getSubFamily(preferences: FontPreferences): String? {
      return preferences.regularSubFamily
    }

    public override fun getRecommendedSubFamily(family: String): String {
      return FontFamilyService.getRecommendedSubFamily(family)
    }
  }

  private inner class BoldFontWeightCombo : FontWeightCombo(true) {

    public override fun getSubFamily(preferences: FontPreferences): String? {
      return preferences.boldSubFamily
    }

    public override fun getRecommendedSubFamily(family: String): String {
      return FontFamilyService.getRecommendedBoldSubFamily(
        family,
        ObjectUtils.notNull(regularWeightCombo?.selectedSubFamily, FontFamilyService.getRecommendedSubFamily(family)))
    }
  }
}

private fun isAdvancedFontFamiliesUI(): Boolean {
  return AppEditorFontOptions.NEW_FONT_SELECTOR
}

private const val FONT_WEIGHT_COMBO_WIDTH = 250

private fun fixComboWidth(combo: FontWeightCombo) {
  val width = JBUI.scale(FONT_WEIGHT_COMBO_WIDTH)

  with(combo) {
    minimumSize = Dimension(width, 0)
    maximumSize = Dimension(width, Int.MAX_VALUE)
    preferredSize = Dimension(width, preferredSize.height)
  }
}