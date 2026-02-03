// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options

import com.intellij.CodeStyleBundle
import com.intellij.application.options.codeStyle.excludedFiles.ExcludedGlobPatternsPanel
import com.intellij.application.options.codeStyle.excludedFiles.ExcludedScopesPanel
import com.intellij.application.options.codeStyle.excludedFiles.GlobPatternDescriptor
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.fields.ExpandableTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected

internal class GeneralCodeStyleFormatterTab() {

  private val patternsField = ExpandableTextField(
    { s -> ExcludedGlobPatternsPanel.toStringList(s) },
    { strings -> StringUtil.join(strings, ExcludedGlobPatternsPanel.PATTERN_SEPARATOR) })

  lateinit var excludedScopesPanel: ExcludedScopesPanel
  lateinit var enableFormatterTags: JBCheckBox
  lateinit var formatterOffTagField: JBTextField
  lateinit var formatterOnTagField: JBTextField
  lateinit var acceptRegularExpressionsCheckBox: JBCheckBox

  private lateinit var conversionMessageRow: Row

  @JvmField
  val panel = panel {
    row(CodeStyleBundle.message("excluded.files.glob.patterns.label")) {
      cell(patternsField)
        .comment(CodeStyleBundle.message("excluded.files.glob.patterns.hint"))
        .align(AlignX.FILL)
    }

    conversionMessageRow = row {
      comment(CodeStyleBundle.message("excluded.files.migration.message"))
    }.visible(false)

    excludedScopesPanel = ExcludedScopesPanel(this)

    row {
      enableFormatterTags = checkBox(ApplicationBundle.message("settings.code.style.general.enable.formatter.tags")).component
    }.topGap(TopGap.SMALL)

    panel {
      indent {
        row(ApplicationBundle.message("settings.code.style.general.formatter.off.tag")) {
          formatterOffTagField = textField().component
        }
        row(ApplicationBundle.message("settings.code.style.general.formatter.on.tag")) {
          formatterOnTagField = textField().component
        }
        row {
          acceptRegularExpressionsCheckBox = checkBox(ApplicationBundle.message("settings.code.style.general.formatter.marker.regexp")).component
        }
      }
    }.enabledIf(enableFormatterTags.selected)
  }

  fun apply(settings: CodeStyleSettings) {
    settings.excludedFiles.setDescriptors(GlobPatternDescriptor.TYPE, ExcludedGlobPatternsPanel.getDescriptors(patternsField))
  }

  fun isModified(settings: CodeStyleSettings): Boolean {
    val result = settings.excludedFiles.getDescriptors(GlobPatternDescriptor.TYPE) != ExcludedGlobPatternsPanel.getDescriptors(patternsField)
    if (!result) conversionMessageRow.visible(false)
    return result
  }

  fun reset(settings: CodeStyleSettings) {
    patternsField.setText(ExcludedGlobPatternsPanel.getPatternsText(settings, conversionMessageRow))
  }
}
