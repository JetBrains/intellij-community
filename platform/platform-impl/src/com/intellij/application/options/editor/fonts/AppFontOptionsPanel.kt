// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor.fonts

import com.intellij.application.options.colors.AbstractFontOptionsPanel
import com.intellij.application.options.colors.ColorAndFontSettingsListener
import com.intellij.application.options.colors.FontGlyphHashCache
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.FontPreferences
import com.intellij.openapi.editor.colors.ModifiableFontPreferences
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl
import com.intellij.openapi.editor.impl.FontFamilyService
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.AbstractFontCombo
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.ButtonsGroup
import com.intellij.ui.dsl.builder.DEFAULT_COMMENT_WIDTH
import com.intellij.ui.dsl.builder.HyperlinkEventAction
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Placeholder
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.actionListener
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.selected
import com.intellij.util.ui.JBUI
import com.jetbrains.JBR
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Dimension
import java.util.function.Consumer
import javax.swing.JComponent
import javax.swing.JLabel

@ApiStatus.Internal
open class AppFontOptionsPanel(private val scheme: EditorColorsScheme) : AbstractFontOptionsPanel() {

  protected val defaultPreferences: FontPreferencesImpl = FontPreferencesImpl()

  private var regularWeightCombo: FontWeightCombo? = null
  private var boldWeightCombo: FontWeightCombo? = null
  private var variantsGroup: ButtonsGroup? = null
  private var variantsPlaceholder: Placeholder? = null
  private var currentFont: String? = null
  private val currentFeatures: MutableMap<String, JBCheckBox> = mutableMapOf()
  private val recentFeatures: MutableMap<String, Set<String>> = mutableMapOf()
  private val fontGlyphCache: FontGlyphHashCache = FontGlyphHashCache()

  init {
    addListener(object : ColorAndFontSettingsListener.Abstract() {
      override fun fontChanged() {
        restoreSelectedVariants()
        updateFontPreferences()
      }

      override fun schemeReset(fontPreferences: FontPreferences) {
        recentFeatures[fontPreferences.fontFamily] = fontPreferences.characterVariants
        if (currentFont == fontPreferences.fontFamily) {
          currentFeatures.forEach { (string, box) -> box.isSelected = string in fontPreferences.characterVariants }
        }
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
    setFontSize(fontSize.toFloat())
  }

  override fun setFontSize(fontSize: Float) {
    scheme.setEditorFontSize(fontSize)
  }

  override fun getLineSpacing(): Float {
    return scheme.lineSpacing
  }

  override fun setCurrentLineSpacing(lineSpacing: Float) {
    scheme.lineSpacing = lineSpacing
  }

  override fun createPrimaryFontCombo(): AbstractFontCombo<*>? {
    return FontFamilyCombo(true)
  }

  override fun createSecondaryFontCombo(): AbstractFontCombo<*>? {
    return FontFamilyCombo(false)
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
    val panel = panel {
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

      if (FontFamilyService.isServiceSupported()) {
        createTypographySettings()
      }
    }.withBorder(JBUI.Borders.empty(BASE_INSET))
    val scrollPane = ScrollPaneFactory.createScrollPane(panel,
                                                        ScrollPaneFactory.VERTICAL_SCROLLBAR_ALWAYS,
                                                        ScrollPaneFactory.HORIZONTAL_SCROLLBAR_NEVER,
                                                        true)
    scrollPane.minimumSize = Dimension(panel.minimumSize.width, 100)
    return scrollPane
  }

  open fun updateFontPreferences() {
    regularWeightCombo?.apply { update(fontPreferences) }
    boldWeightCombo?.apply { update(fontPreferences) }
    refreshVariantsPanel()
  }

  private fun refreshVariantsPanel() {
    if (variantsGroup == null) return
    val font = FontFamilyService.getFont(fontPreferences.fontFamily, fontPreferences.regularSubFamily, fontPreferences.boldSubFamily, 12)
    val availableFeatures = JBR.getFontExtensions().getAvailableFeatures(font)
      .mapNotNull { variant ->
        val displayText = getFeatureLabel(variant)
        if (displayText != null) { variant to displayText } else { null }
      }

    if (availableFeatures.isEmpty()) {
      variantsGroup?.visible(false)
      return
    }
    variantsGroup?.visible(true)

    val enabledFeatures = fontPreferences.characterVariants
    if (fontPreferences.fontFamily != currentFont) {
      // Font family changed: update checkboxes
      currentFeatures.clear()
      currentFont = fontPreferences.fontFamily
      variantsPlaceholder!!.component = panel {
        availableFeatures
          .sortedBy { getFeatureSortingKey(it.first) }
          .forEach { (feature: String, displayText: @Nls String) ->
            addFeatureCheckbox(feature, displayText, feature in enabledFeatures)
          }
      }
    } else {
      // Same font: variants might have changed
      currentFeatures.forEach { (feature, cb) -> cb.isSelected = feature in enabledFeatures }
    }
  }

  private fun restoreSelectedVariants() {
    // Save previous variants
    currentFont?.let { font -> recentFeatures[font] = currentFeatures.filter { (_, value) -> value.isSelected }.keys.toSet() }
    // Restore variants for fontPreferences.fontFamily
    val previousFeatures = recentFeatures[fontPreferences.fontFamily]
    if (previousFeatures != null && fontPreferences.characterVariants != previousFeatures) {
      (fontPreferences as ModifiableFontPreferences).characterVariants = previousFeatures
    }
  }

  protected open fun createCustomComponent() : JComponent? = null

  private fun Panel.createTypographySettings() {
    collapsibleGroup(ApplicationBundle.message("settings.editor.font.typography.settings")) {
      row(ApplicationBundle.message("settings.editor.font.main.weight")) {
        val component = createRegularWeightCombo()
        regularWeightCombo = component
        cell(component).widthGroup("TypographySettingsCombo")
      }

      row(ApplicationBundle.message("settings.editor.font.bold.weight")) {
        val component = createBoldWeightCombo()
        boldWeightCombo = component
        val boldFontHint = createBoldFontHint()
        cell(component)
          .widthGroup("TypographySettingsCombo")
          .comment(boldFontHint.first, DEFAULT_COMMENT_WIDTH, boldFontHint.second)
      }.bottomGap(BottomGap.SMALL)

      val secondaryFont = JLabel(ApplicationBundle.message("secondary.font"))
      setSecondaryFontLabel(secondaryFont)
      row(secondaryFont) {
        cell(secondaryCombo)
          .widthGroup("TypographySettingsCombo")
          .comment(ApplicationBundle.message("label.fallback.fonts.list.description"))
      }

      variantsGroup = buttonsGroup(ApplicationBundle.message("settings.editor.font.character.variants"), true) {
        row {
          variantsPlaceholder = placeholder()
        }
      }
    }
  }

  protected open fun createBoldFontHint(): Pair<@Nls String?, HyperlinkEventAction> =
    Pair(null, HyperlinkEventAction.HTML_HYPERLINK_INSTANCE)

  private fun createRegularWeightCombo(): FontWeightCombo {
    val result = RegularFontWeightCombo()

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

  private val previewChars = ('!'..'~').joinToString()

  private fun Panel.addFeatureCheckbox(feature: String, label: @Nls String, selected: Boolean) {
    row {
      val featureCb = checkBox(label)
        .selected(selected)
        .actionListener { _, cb ->
          (fontPreferences as ModifiableFontPreferences).apply {
            setCharacterVariant(feature, cb.isSelected)
          }
          fireFontChanged()
        }
        .component

      currentFeatures[feature] = featureCb

      FontDiffPopup.diffForFeatures(fontGlyphCache, scheme, previewChars, feature)
        .installOn(featureCb)
    }
  }

  private fun getFeatureSortingKey(feature: String): String {
    if (feature.take(2) == "ss" && feature.substring(2).toIntOrNull() != null) {
      return "0-$feature"
    }
    if (feature.take(2) == "cv" && feature.substring(2).toIntOrNull() != null) {
      return "Z-$feature"
    }
    when (feature) {
      "zero" -> return "1"
      "case", "clig", "dlig", "hlig", "onum", "salt" -> return "2-$feature"
      else -> return "X-$feature"
    }
  }

  private fun getFeatureLabel(@NlsSafe tag: String): @Nls String? {
    // Known discrete tags
    return when (tag) {
      "case" -> ApplicationBundle.message("settings.editor.font.feature.case")
      "clig" -> ApplicationBundle.message("settings.editor.font.feature.clig")
      "dlig" -> ApplicationBundle.message("settings.editor.font.feature.dlig")
      "hlig" -> ApplicationBundle.message("settings.editor.font.feature.hlig")
      "onum" -> ApplicationBundle.message("settings.editor.font.feature.onum")
      "salt" -> ApplicationBundle.message("settings.editor.font.feature.salt")
      "zero" -> ApplicationBundle.message("settings.editor.font.feature.zero")
      else -> {
        // Ranged tags: ss01-ss20, cv01-cv99
        if (tag.length == 4 && tag.startsWith("ss")) {
          val num = tag.substring(2).toIntOrNull()
          if (num != null) return ApplicationBundle.message("settings.editor.font.feature.ss", num)
        }
        if (tag.length == 4 && tag.startsWith("cv")) {
          val num = tag.substring(2).toIntOrNull()
          if (num != null) return ApplicationBundle.message("settings.editor.font.feature.cv", num)
        }
        null
      }
    }
  }

  private class RegularFontWeightCombo : FontWeightCombo(false) {

    override fun getSubFamily(preferences: FontPreferences): String? {
      return preferences.regularSubFamily
    }

    override fun getRecommendedSubFamily(family: String): String {
      return FontFamilyService.getRecommendedSubFamily(family)
    }
  }

  private inner class BoldFontWeightCombo : FontWeightCombo(true) {

    override fun getSubFamily(preferences: FontPreferences): String? {
      return preferences.boldSubFamily
    }

    override fun getRecommendedSubFamily(family: String): String {
      return FontFamilyService.getRecommendedBoldSubFamily(
        family,
        ((regularWeightCombo?.selectedSubFamily) ?: FontFamilyService.getRecommendedSubFamily(family)))
    }
  }

  override fun updateOptionsList() {
    super.updateOptionsList()
    regularWeightCombo?.isEnabled = !isReadOnly
    boldWeightCombo?.isEnabled = !isReadOnly
  }
}