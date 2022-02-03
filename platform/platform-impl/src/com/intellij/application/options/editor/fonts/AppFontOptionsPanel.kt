// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.editor.fonts

import com.intellij.application.options.colors.AbstractFontOptionsPanel
import com.intellij.application.options.colors.ColorAndFontSettingsListener
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.FontPreferences
import com.intellij.openapi.editor.colors.ModifiableFontPreferences
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl
import com.intellij.openapi.editor.impl.FontFamilyService
import com.intellij.ui.AbstractFontCombo
import com.intellij.ui.components.Label
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ObjectUtils
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.function.Consumer
import javax.swing.JComponent

open class AppFontOptionsPanel(private val scheme: EditorColorsScheme) : AbstractFontOptionsPanel() {

  protected val defaultPreferences = FontPreferencesImpl()

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

  override fun addControls() {
    layout = BorderLayout()
    add(createControls(), BorderLayout.CENTER)
  }

  fun updateOnChangedFont() {
    updateOptionsList()
    fireFontChanged()
  }

  override fun createControls(): JComponent {
    return panel {
      row(primaryLabel) {
        cell(primaryCombo)
      }

      row(sizeLabel) {
        cell(editorFontSizeField)

        cell(lineSpacingField)
          .label(lineSpacingLabel)
      }.bottomGap(BottomGap.SMALL)

      row {
        cell(enableLigaturesCheckbox)
          .gap(RightGap.SMALL)
        cell(enableLigaturesHintLabel)
      }.layout(RowLayout.INDEPENDENT)
        .bottomGap(BottomGap.SMALL)

      val customComponent = createCustomComponent()
      if (customComponent != null) {
        row {
          cell(customComponent)
        }
      }

      createTypographySettings()
    }.withBorder(JBUI.Borders.empty(BASE_INSET))
  }

  open fun updateFontPreferences() {
    regularWeightCombo?.apply { update(fontPreferences) }
    boldWeightCombo?.apply { update(fontPreferences) }
  }

  protected open fun createCustomComponent() : JComponent? = null

  private fun Panel.createTypographySettings() {
    collapsibleGroup(ApplicationBundle.message("settings.editor.font.typography.settings"), indent = false) {
      if (isAdvancedFontFamiliesUI()) {
        row(ApplicationBundle.message("settings.editor.font.main.weight")) {
          val component = createRegularWeightCombo()
          regularWeightCombo = component
          cell(component)
        }

        row(ApplicationBundle.message("settings.editor.font.bold.weight")) {
          val component = createBoldWeightCombo()
          boldWeightCombo = component
          val boldFontHint = createBoldFontHint()
          cell(component)
            .comment(boldFontHint.first, DEFAULT_COMMENT_WIDTH, boldFontHint.second)
        }.bottomGap(BottomGap.SMALL)
      }

      val secondaryFont = Label(ApplicationBundle.message("secondary.font"))
      setSecondaryFontLabel(secondaryFont)
      row(secondaryFont) {
        cell(secondaryCombo)
          .comment(ApplicationBundle.message("label.fallback.fonts.list.description"))
      }
    }
  }

  protected open fun createBoldFontHint(): Pair<@Nls String?, HyperlinkEventAction> =
    Pair(null, HyperlinkEventAction.HTML_HYPERLINK_INSTANCE)

  private fun createRegularWeightCombo(): FontWeightCombo {
    val result = RegularFontWeightCombo()
    fixComboWidth(result)

    result.addActionListener {
      changeFontPreferences { preferences: FontPreferences ->
        if (preferences is ModifiableFontPreferences) {
          val newSubFamily = result.selectedSubFamily
          if (preferences.regularSubFamily != newSubFamily) {
            preferences.boldSubFamily = null // Reset bold subfamily for a different regular
          }
          preferences.regularSubFamily = newSubFamily
        }
      }
    }

    return result
  }

  private fun createBoldWeightCombo(): FontWeightCombo {
    val result = BoldFontWeightCombo()
    fixComboWidth(result)

    result.addActionListener {
      changeFontPreferences { preferences: FontPreferences ->
        if (preferences is ModifiableFontPreferences) {
          preferences.boldSubFamily = result.selectedSubFamily
        }
      }
    }

    return result
  }

  private fun changeFontPreferences(consumer: Consumer<FontPreferences>) {
    val preferences = fontPreferences
    consumer.accept(preferences)
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

  override fun updateOptionsList() {
    super.updateOptionsList()
    regularWeightCombo?.isEnabled = !isReadOnly
    boldWeightCombo?.isEnabled = !isReadOnly
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