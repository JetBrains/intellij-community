// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.parameters

import com.intellij.codeInsight.hints.declarative.InlayHintsCustomSettingsProvider
import com.intellij.codeInsight.hints.settings.ParameterHintsSettingsPanel
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
abstract class AbstractDeclarativeParameterHintsCustomSettingsProvider : InlayHintsCustomSettingsProvider<Unit> {
  override fun createComponent(project: Project, language: Language): JComponent =
    ParameterHintsSettingsPanel(ParameterHintsExcludeListService.getInstance().getConfig(language) ?: error("No exclude list config for $language registered!"))

  // ExcludeListDialog takes care of the settings state, hence the following methods do nothing
  override fun isDifferentFrom(project: Project, settings: Unit): Boolean = false

  override fun getSettingsCopy() {}

  override fun putSettings(project: Project, settings: Unit, language: Language) {}

  override fun persistSettings(project: Project, settings: Unit, language: Language) {}
}