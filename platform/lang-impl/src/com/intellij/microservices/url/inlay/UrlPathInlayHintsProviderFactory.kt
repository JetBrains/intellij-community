// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.microservices.url.inlay

import com.intellij.codeInsight.hints.InlayHintsProviderFactory
import com.intellij.codeInsight.hints.ProviderInfo
import com.intellij.lang.Language
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.TestOnly

internal class UrlPathInlayHintsProviderFactory : InlayHintsProviderFactory {
  override fun getProvidersInfo(): List<ProviderInfo<out Any>> {
    return EP_NAME.extensionList.flatMap {
      it.languages.map { language -> ProviderInfo(language, UrlPathInlayHintsProvider(it)) }
    }
  }
}

private val EP_NAME = ExtensionPointName.create<UrlPathInlayLanguagesProvider>("com.intellij.microservices.urlInlayLanguagesProvider")

internal fun getLanguagesProviderByLanguage(language: Language): UrlPathInlayLanguagesProvider? =
  EP_NAME.extensionList.find { language in it.languages }

@TestOnly
val urlPathInlayHintsProviderFactory: InlayHintsProviderFactory = UrlPathInlayHintsProviderFactory()