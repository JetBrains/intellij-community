// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle

import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizableOptions
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.panel

internal class CodeStyleBlankLinesUI(options: CodeStyleSettingsCustomizableOptions,
                                     private val renamedFields: Map<String, @NlsContexts.Label String>,
                                     keepBlankLinesOptions: List<CodeStyleBlankLinesPanel.IntOption>,
                                     blankLinesOptions: List<CodeStyleBlankLinesPanel.IntOption>) {

  @JvmField
  val panel = panel {
    addGroup(options.BLANK_LINES_KEEP, keepBlankLinesOptions)
    addGroup(options.BLANK_LINES, blankLinesOptions)
  }

  private fun Panel.addGroup(@NlsContexts.BorderTitle title: String, intOptions: List<CodeStyleBlankLinesPanel.IntOption>) {
    if (intOptions.isNotEmpty()) {
      group(title) {
        for (intOption in intOptions) {
          row(renamedFields.getOrDefault(intOption.optionName, intOption.myLabel)) {
            cell(intOption.myIntField)
          }
        }
      }
    }
  }
}
