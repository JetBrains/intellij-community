// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative

import com.intellij.lang.Language
import com.intellij.openapi.extensions.ExtensionPointName

/**
 * Allows to register providers from one family for multiple languages.
 */
interface InlayHintsProviderFactory {
  companion object {
    val EP: ExtensionPointName<InlayHintsProviderFactory> = ExtensionPointName("com.intellij.codeInsight.declarativeInlayProviderFactory")

    /**
     * @return list of potentially available providers for a particular language (not filtering enabled ones)
     */
    fun getProvidersForLanguage(language: Language) : List<InlayProviderInfo> {
      return EP.extensionList.flatMap { it.getProvidersForLanguage(language) }
    }

    fun getProviderInfo(language: Language, providerId: String) : InlayProviderInfo? {
      for (factory in EP.extensionList) {
        val providerInfo = factory.getProviderInfo(language, providerId)
        if (providerInfo != null) {
          return providerInfo
        }
      }
      return null
    }
  }

  /**
   * @return list of providers which may be run on a file with specific language.
   */
  fun getProvidersForLanguage(language: Language) : List<InlayProviderInfo>

  /**
   * List of languages for which theoretically this factory may create providers (or may not).
   */
  fun getSupportedLanguages() : Set<Language>

  /**
   * Searches for provider info by id of the provider. Must provide one of the providers of [getProvidersForLanguage].
   */
  fun getProviderInfo(language: Language, providerId: String) : InlayProviderInfo?
}