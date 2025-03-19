package com.intellij.database.settings

import com.intellij.application.options.colors.AbstractFontOptionsPanel
import com.intellij.application.options.colors.ColorAndFontSettingsListener
import com.intellij.database.run.ui.grid.GridColorsScheme
import com.intellij.openapi.editor.colors.DelegatingFontPreferences
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.FontPreferences
import com.intellij.openapi.editor.colors.impl.FontPreferencesImpl
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent
import javax.swing.JPanel

class DataGridFontOptionsPanel(val scheme: GridColorsScheme, private val updateCallback: () -> Unit = { }) {
  lateinit var content: DialogPanel

  private val fontOptionsPanel = object : AbstractFontOptionsPanel(false) {
    override fun isReadOnly(): Boolean = false
    override fun isDelegating(): Boolean = false
    public override fun getFontPreferences(): FontPreferences = scheme.fontPreferences
    override fun getLineSpacing(): Float = scheme.lineSpacing

    override fun setFontSize(fontSize: Int) {
      scheme.editorFontSize = fontSize
    }

    override fun setFontSize(fontSize: Float) {
      setFontSize((fontSize + 0.5).toInt())
    }

    override fun setCurrentLineSpacing(lineSpacing: Float) {
      scheme.lineSpacing = lineSpacing
    }

    override fun createControls(): JComponent {
      content = panel {
        row {
          cell(primaryCombo)
          cell(editorFontSizeField)
            .label(sizeLabel)
          cell(lineSpacingField)
            .label(lineSpacingLabel)
        }
      }

      return JPanel() // Not used
    }

    override fun setDelegatingPreferences(isDelegating: Boolean) {
      setDelegatingPreferencesImpl(isDelegating)
    }
  }.apply {
    addListener(object : ColorAndFontSettingsListener {
      override fun selectedOptionChanged(selected: Any) {
      }

      override fun schemeChanged(source: Any) {
      }

      override fun settingsChanged() {
      }

      override fun selectionInPreviewChanged(typeToSelect: String) {
      }

      override fun fontChanged() {
        updateCallback()
      }

    })
  }

  fun setDelegatingPreferencesImpl(isDelegating: Boolean) {
    val globalFontPreferences = EditorColorsManager.getInstance().globalScheme.fontPreferences
    scheme.setCustomFontPreferences(if (isDelegating) DelegatingFontPreferences { globalFontPreferences }
                                    else FontPreferencesImpl().apply { globalFontPreferences.copyTo(this) })
    fontOptionsPanel.updateOptionsList()
  }

  fun apply(useCustomFont: Boolean) {
    val settings = DataGridAppearanceSettings.getSettings()
    settings.useGridCustomFont = useCustomFont
    val family: String = fontOptionsPanel.fontPreferences.fontFamily
    settings.gridFontFamily = if (useCustomFont) family else null
    settings.gridFontSize = if (useCustomFont) fontOptionsPanel.fontPreferences.getSize(family) else -1
    settings.gridLineSpacing = (if (useCustomFont) fontOptionsPanel.fontPreferences.lineSpacing else (-1).toFloat())
  }

  fun isModified(useCustomFont: Boolean): Boolean {
    if (!useCustomFont) return false
    val settings = DataGridAppearanceSettings.getSettings()
    val family: String = fontOptionsPanel.fontPreferences.fontFamily
    if (settings.gridFontFamily != family) return true
    if (settings.gridFontSize != fontOptionsPanel.fontPreferences.getSize(family)) return true
    if (settings.gridLineSpacing != fontOptionsPanel.fontPreferences.lineSpacing) return true
    return false
  }

  fun reset() {
    val settings = DataGridAppearanceSettings.getSettings()
    scheme.updateFromSettings(settings)
    fontOptionsPanel.updateOptionsList()
  }
}