// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.codeStyle.excludedFiles

import com.intellij.CodeStyleBundle
import com.intellij.application.options.codeStyle.CodeStyleSchemesModel
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.TopGap

internal val isScopeBasedFormattingUI: Boolean
  get() = Registry.`is`("editor.scope.based.formatting.ui", false)

/**
 * Legacy panel for excluded scopes. Shown only if a user has already defined scope-based exclusions.
 */
internal class ExcludedScopesPanel(parentPanel: Panel) {

  private val excludedFilesList = ExcludedFilesList()
  private val content: Row

  init {
    excludedFilesList.initModel()

    with(parentPanel) {
      content = row {
        cell(excludedFilesList.decorator.createPanel())
          .label(CodeStyleBundle.message("excluded.files.do.not.format.scope"), position = LabelPosition.TOP)
          .comment(CodeStyleBundle.message("excluded.files.deprecation.label.text"))
          .align(AlignX.FILL)
      }.topGap(TopGap.SMALL)
    }
  }

  fun apply(settings: CodeStyleSettings) {
    excludedFilesList.apply(settings)
    updateVisibility()
  }

  fun reset(settings: CodeStyleSettings) {
    excludedFilesList.reset(settings)
    updateVisibility()
  }

  fun isModified(settings: CodeStyleSettings): Boolean {
    return excludedFilesList.isModified(settings)
  }

  fun setSchemesModel(model: CodeStyleSchemesModel) {
    excludedFilesList.setSchemesModel(model)
  }

  private fun updateVisibility() {
    if (excludedFilesList.isEmpty) {
      content.visible(isScopeBasedFormattingUI)
    }
  }
}
