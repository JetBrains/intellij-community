// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.openapi.extensions.ExtensionPointName

private const val EXTENSION_POINT_NAME = "com.intellij.codeInsight.inlayProvider"

object InlayHintsProviderExtension : LanguageExtension<InlayHintsProvider<*>>(EXTENSION_POINT_NAME) {
  private fun findLanguagesWithHintsSupport(): List<Language> {
    val extensionPointName = inlayProviderName
    return extensionPointName.extensionList.map { it.language }
      .toSet()
      .mapNotNull { Language.findLanguageByID(it) }
  }

  fun findProviders() : List<ProviderInfo<*>> {
    return findLanguagesWithHintsSupport().flatMap { language ->
      InlayHintsProviderExtension.allForLanguage(language).map { ProviderInfo(language, it) }
    }
  }

  val inlayProviderName: ExtensionPointName<InlayHintsProviderExtensionBean> = ExtensionPointName(EXTENSION_POINT_NAME)
}