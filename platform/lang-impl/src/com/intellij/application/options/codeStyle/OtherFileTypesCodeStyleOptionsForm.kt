// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle

import com.intellij.application.options.CodeStyleAbstractPanel
import com.intellij.application.options.IndentOptionsEditor
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.highlighter.EditorHighlighter
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.options.ConfigurationException
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.ApiStatus
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Used for non-language settings (if file type is not supported by Intellij IDEA), for example, plain text.
 */
@ApiStatus.Internal
class OtherFileTypesCodeStyleOptionsForm internal constructor(settings: CodeStyleSettings) : CodeStyleAbstractPanel(settings) {
  private val myIndentOptionsEditor = IndentOptionsEditorWithSmartTabs()
  private val myTopPanel: JPanel

  init {
    val indentOptionsPanel = myIndentOptionsEditor.createPanel()
    myTopPanel = panel {
      indent {
        row {
          label(ApplicationBundle.message("code.style.other.label"))
        }
        row {
          cell(indentOptionsPanel)
        }
      }
    }
    addPanelToWatch(indentOptionsPanel)
  }

  override fun getRightMargin(): Int = 0

  override fun createHighlighter(scheme: EditorColorsScheme): EditorHighlighter? = null

  override fun getFileType(): FileType = FileTypes.PLAIN_TEXT

  override fun getPreviewText(): String? = null

  @Throws(ConfigurationException::class)
  override fun apply(settings: CodeStyleSettings) {
    myIndentOptionsEditor.apply(settings, settings.OTHER_INDENT_OPTIONS)
  }

  override fun isModified(settings: CodeStyleSettings): Boolean {
    return myIndentOptionsEditor.isModified(settings, settings.OTHER_INDENT_OPTIONS)
  }

  override fun getPanel(): JComponent? = myTopPanel

  override fun resetImpl(settings: CodeStyleSettings) {
    myIndentOptionsEditor.reset(settings, settings.OTHER_INDENT_OPTIONS)
  }
}

private class IndentOptionsEditorWithSmartTabs : IndentOptionsEditor() {
  private lateinit var myCbSmartTabs: JCheckBox

  override fun addTabOptions() {
    super.addTabOptions()
    myCbSmartTabs = JCheckBox(ApplicationBundle.message("checkbox.indent.smart.tabs"))
    add(myCbSmartTabs, true)
  }

  override fun reset(settings: CodeStyleSettings, options: CommonCodeStyleSettings.IndentOptions) {
    super.reset(settings, options)
    myCbSmartTabs.isSelected = options.SMART_TABS
  }

  override fun isModified(settings: CodeStyleSettings, options: CommonCodeStyleSettings.IndentOptions): Boolean {
    return super.isModified(settings, options) || isFieldModified(myCbSmartTabs, options.SMART_TABS)
  }

  override fun apply(settings: CodeStyleSettings, options: CommonCodeStyleSettings.IndentOptions) {
    super.apply(settings, options)
    options.SMART_TABS = myCbSmartTabs.isSelected
  }
}
