// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options.editor.fonts

import com.intellij.application.options.colors.ColorAndFontOptions
import com.intellij.ide.DataManager
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.colors.ModifiableFontPreferences
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.OptionsBundle
import com.intellij.openapi.options.colors.pages.GeneralColorsPage
import com.intellij.openapi.options.ex.Settings
import com.intellij.ui.components.ActionLink
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.HyperlinkEventAction
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class AppEditorFontOptionsPanel(scheme: EditorColorsScheme) : AppFontOptionsPanel(scheme) {
  private lateinit var restoreDefaults: Cell<ActionLink>

  override fun createCustomComponent(): JComponent {
    return panel {
      row {
        comment(ApplicationBundle.message("comment.use.ligatures.with.reader.mode")) {
          goToReaderMode()
        }
      }

      row {
        restoreDefaults = link(ApplicationBundle.message("settings.editor.font.restored.defaults")) {
          restoreDefaults()
        }
      }
    }
  }

  private fun restoreDefaults() {
    AppEditorFontOptions.initDefaults(fontPreferences as ModifiableFontPreferences)
    updateOnChangedFont()
  }

  override fun updateFontPreferences() {
    restoreDefaults.enabled(defaultPreferences != fontPreferences)
    super.updateFontPreferences()
  }

  override fun createBoldFontHint(): Pair<String?, HyperlinkEventAction> =
    Pair(ApplicationBundle.message("settings.editor.font.bold.weight.hint"),
         HyperlinkEventAction { navigateToColorSchemeTextSettings() })

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

}