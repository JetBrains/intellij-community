// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options

import com.intellij.application.options.codeStyle.excludedFiles.ExcludedGlobPatternsPanel
import com.intellij.application.options.codeStyle.excludedFiles.ExcludedScopesPanel
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected

internal class GeneralCodeStyleFormatterTab() {

  @JvmField
  val excludedPatternsPanel: ExcludedGlobPatternsPanel = ExcludedGlobPatternsPanel()

  lateinit var excludedScopesPanel: ExcludedScopesPanel
  lateinit var enableFormatterTags: JBCheckBox
  lateinit var formatterOffTagField: JBTextField
  lateinit var formatterOnTagField: JBTextField
  lateinit var acceptRegularExpressionsCheckBox: JBCheckBox

  @JvmField
  val panel = panel {
    row {
      cell(excludedPatternsPanel).align(AlignX.FILL)
    }.bottomGap(BottomGap.SMALL)

    excludedScopesPanel = ExcludedScopesPanel(this)

    row {
      enableFormatterTags = checkBox(ApplicationBundle.message("settings.code.style.general.enable.formatter.tags")).component
    }

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
    }.enabledIf(enableFormatterTags.selected)
  }
}
