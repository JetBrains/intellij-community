// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.lang.Language
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

/**
 * Factory for [InlayHintsProvider], can be used to support multiple languages with a single type of inlay hints.
 *
 * Method without project is preferable.
 */
interface InlayHintsProviderFactory {
  @Deprecated("Use getProvidersInfo without project", ReplaceWith("getProvidersInfo()"))
  fun getProvidersInfo(project: Project): List<ProviderInfo<out Any>> = getProvidersInfo()

  /**
   * Consider implementing [getProvidersInfoForLanguage] and [getLanguages] to avoid triggering cascade of classes to load provider for unrelated language
   */
  fun getProvidersInfo(): List<ProviderInfo<out Any>> = emptyList()

  fun getProvidersInfoForLanguage(language: Language): List<InlayHintsProvider<out Any>> {
    val providersInfo = getProvidersInfo()
    return providersInfo.filter { it.language == language }.map { it.provider }
  }

  fun getLanguages() : Iterable<Language> {
    return getProvidersInfo().map { it.language }
  }

  companion object {
    @JvmStatic
    val EP: ExtensionPointName<InlayHintsProviderFactory> = ExtensionPointName<InlayHintsProviderFactory>("com.intellij.codeInsight.inlayProviderFactory")
  }
}

class ProviderInfo<T : Any>(
  val language: Language,
  val provider: InlayHintsProvider<T>

) {
  override fun toString(): String {
    return language.displayName + ": " + provider.javaClass.name
  }
}

