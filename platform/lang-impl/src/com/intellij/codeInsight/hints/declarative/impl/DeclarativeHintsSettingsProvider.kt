// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.DeclarativeInlayHintsSettings
import com.intellij.codeInsight.hints.declarative.InlayHintsProviderExtensionBean
import com.intellij.codeInsight.hints.settings.InlayProviderSettingsModel
import com.intellij.codeInsight.hints.settings.InlaySettingsProvider
import com.intellij.lang.Language
import com.intellij.openapi.project.Project

class DeclarativeHintsSettingsProvider : InlaySettingsProvider {
  override fun createModels(project: Project, language: Language): List<InlayProviderSettingsModel> {
    val providerDescriptions = InlayHintsProviderExtensionBean.EP.extensionList
    val settings = DeclarativeInlayHintsSettings.getInstance(project)
    return providerDescriptions
      .filter { Language.findLanguageByID(it.language) == language }
      .map {
      val isEnabled = settings.isProviderEnabled(it.requiredProviderId()) ?: it.isEnabledByDefault
      DeclarativeHintsProviderSettingsModel(it, isEnabled, language, project)
    }
  }

  override fun getSupportedLanguages(project: Project): Collection<Language> {
    return InlayHintsProviderExtensionBean.EP.extensionList.asSequence()
      .map { Language.findLanguageByID(it.language!!)!! }
      .toHashSet()
  }
}