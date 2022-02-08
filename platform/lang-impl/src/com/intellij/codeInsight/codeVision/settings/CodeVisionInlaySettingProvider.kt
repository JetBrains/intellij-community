// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision.settings

import com.intellij.codeInsight.hints.settings.InlayProviderSettingsModel
import com.intellij.codeInsight.hints.settings.InlaySettingsProvider
import com.intellij.lang.Language
import com.intellij.openapi.project.Project

class CodeVisionInlaySettingProvider : InlaySettingsProvider {
  override fun createModels(project: Project, language: Language): List<InlayProviderSettingsModel> {
    if (language != Language.ANY) return emptyList()
    val codeVisionGroupModels = CodeVisionGroupSettingProvider.EP.findGroupModels() + CodeVisionGroupSettingProvider.EP.findSingleModels(project)
    return codeVisionGroupModels.map { it.createModel(project) }
  }

  override fun getSupportedLanguages(project: Project) = listOf(Language.ANY)
}