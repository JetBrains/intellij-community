// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.lang.Language
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
class SingleLanguageInlayProviderFactory : InlayHintsProviderFactory {
  override fun getProvidersInfo(): List<ProviderInfo<out Any>> = InlayHintsProviderExtension.findProviders()

  override fun getProvidersInfoForLanguage(language: Language): List<InlayHintsProvider<out Any>> {
    val extensionList = InlayHintsProviderExtension.inlayProviderName.extensionList
    return extensionList.filter {
      language.isKindOf(it.language ?: error("InlayHintsProvider ${it.settingsKeyId} must have a language"))
    }.map { it.instance }
  }

  override fun getLanguages(): Iterable<Language> {
    val extensionList = InlayHintsProviderExtension.inlayProviderName.extensionList
    return extensionList.map { Language.findLanguageByID(it.language) ?: error("Language ${it.language} not found for ${it.settingsKeyId}") }
  }
}