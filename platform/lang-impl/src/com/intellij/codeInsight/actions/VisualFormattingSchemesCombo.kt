// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions

import com.intellij.application.options.codeStyle.CodeStyleSchemesModel
import com.intellij.application.options.schemes.SchemesCombo
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleScheme
import com.intellij.psi.codeStyle.CodeStyleSchemes
import com.intellij.ui.SimpleTextAttributes

internal class VisualFormattingSchemesCombo(project: Project) : SchemesCombo<CodeStyleScheme>() {
  private val codeStyleSchemesModel = CodeStyleSchemesModel(project)
  override fun supportsProjectSchemes(): Boolean = true

  override fun isProjectScheme(scheme: CodeStyleScheme): Boolean = codeStyleSchemesModel.isProjectScheme(scheme)

  override fun getSchemeAttributes(scheme: CodeStyleScheme?): SimpleTextAttributes = SimpleTextAttributes.REGULAR_ATTRIBUTES

  fun onReset(settings: ReaderModeSettings) {
    resetSchemes(codeStyleSchemesModel.allSortedSchemes)
    selectScheme(getVisualFormattingLayerScheme(settings))
  }

  fun onApply(settings: ReaderModeSettings) {
    val selected = selectedScheme
    settings.visualFormattingChosenScheme = if (selected != null) {
      ReaderModeSettings.Scheme(selected.name, codeStyleSchemesModel.isProjectScheme(selected))
    }
    else {
      ReaderModeSettings.Scheme()
    }
  }

  fun onIsModified(settings: ReaderModeSettings): Boolean {
    val selected = selectedScheme
    return selected == null ||
           selected.name != settings.visualFormattingChosenScheme.name ||
           codeStyleSchemesModel.isProjectScheme(selected) != settings.visualFormattingChosenScheme.isProjectLevel
  }

  private fun getVisualFormattingLayerScheme(settings: ReaderModeSettings): CodeStyleScheme =
    codeStyleSchemesModel.schemes.find {
      settings.visualFormattingChosenScheme.name == it.name &&
      settings.visualFormattingChosenScheme.isProjectLevel == codeStyleSchemesModel.isProjectScheme(it)
    } ?: CodeStyleSchemes.getInstance().defaultScheme
}