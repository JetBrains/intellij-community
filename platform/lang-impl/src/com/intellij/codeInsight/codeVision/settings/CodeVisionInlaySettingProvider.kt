// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision.settings

import com.intellij.codeInsight.codeVision.CodeVisionProviderFactory
import com.intellij.codeInsight.hints.settings.InlayProviderSettingsModel
import com.intellij.codeInsight.hints.settings.InlaySettingsProvider
import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class CodeVisionInlaySettingProvider : InlaySettingsProvider {
  override fun createModels(project: Project, language: Language): List<InlayProviderSettingsModel> {
    if (!Registry.`is`("editor.codeVision.new")) return emptyList()
    if (language != Language.ANY) return emptyList()
    val providers = CodeVisionProviderFactory.createAllProviders(project)
    val groupIdsOfExistingProviders = providers.asSequence().filter { it.isAvailableFor(project) }.map { it.groupId }.toHashSet()
    val codeVisionGroupModels = CodeVisionGroupSettingProvider.EP.findGroupModels() + CodeVisionGroupSettingProvider.EP.findSingleModels(project)
    return codeVisionGroupModels.filter { it.groupId in groupIdsOfExistingProviders }.map { it.createModel(project) }
  }

  override fun getSupportedLanguages(project: Project): List<Language> = listOf(Language.ANY)
}