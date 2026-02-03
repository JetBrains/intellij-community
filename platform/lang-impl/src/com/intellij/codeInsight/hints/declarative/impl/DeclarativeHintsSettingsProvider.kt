// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.DeclarativeInlayHintsSettings
import com.intellij.codeInsight.hints.declarative.InlayHintsProviderExtensionBean
import com.intellij.codeInsight.hints.settings.InlayProviderSettingsModel
import com.intellij.codeInsight.hints.settings.InlaySettingsProvider
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class DeclarativeHintsSettingsProvider : InlaySettingsProvider {
  override fun createModels(project: Project, language: Language): List<InlayProviderSettingsModel> {
    val providerDescriptions = InlayHintsProviderExtensionBean.EP.extensionList
    val settings = DeclarativeInlayHintsSettings.getInstance()
    return providerDescriptions
      .filter { Language.findLanguageByID(it.language) == language }
      .map {
      val isEnabled = settings.isProviderEnabled(it.requiredProviderId()) ?: it.isEnabledByDefault
      DeclarativeHintsProviderSettingsModel(it, isEnabled, language, project)
    }
  }

  override fun getSupportedLanguages(project: Project): Collection<Language> {
    return InlayHintsProviderExtensionBean.EP.extensionList.asSequence()
      .mapNotNull { Language.findLanguageByID(it.language!!) ?: error("Language with id ${it.language} not found") }
      .toHashSet()
  }
}