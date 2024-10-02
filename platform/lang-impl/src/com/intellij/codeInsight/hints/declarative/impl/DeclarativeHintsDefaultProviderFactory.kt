// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.InlayHintsProviderExtensionBean
import com.intellij.codeInsight.hints.declarative.InlayHintsProviderFactory
import com.intellij.codeInsight.hints.declarative.InlayOptionInfo
import com.intellij.codeInsight.hints.declarative.InlayProviderInfo
import com.intellij.lang.Language
import com.intellij.lang.MetaLanguage
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class DeclarativeHintsDefaultProviderFactory : InlayHintsProviderFactory {
  companion object {
    private val logger: Logger = logger<DeclarativeHintsDefaultProviderFactory>()
  }
  override fun getProvidersForLanguage(language: Language): List<InlayProviderInfo> {
    val beans = InlayHintsProviderExtensionBean.EP.extensionList.filter {
      val beanLanguage = Language.findLanguageByID(it.language) ?: return@filter false
      if (language.isKindOf(beanLanguage)) {
        return@filter true
      }
      if (beanLanguage !is MetaLanguage) {
        return@filter false
      }
      return@filter beanLanguage.matchesLanguage(language)
    }
    return beans.map {
      val options = it.options
        .map { option -> InlayOptionInfo(option.requireOptionId(), option.enabledByDefault, option.getName(it)) }
        .toSet()
      InlayProviderInfo(it.instance, it.providerId!!, options, it.isEnabledByDefault, it.getProviderName())
    }
  }

  override fun getSupportedLanguages(): Set<Language> {
    val extensions = InlayHintsProviderExtensionBean.EP.extensionList
    val languages = HashSet<Language>()
    for (extension in extensions) {
      val language = Language.findLanguageByID(extension.language)
      if (language == null) {
        logger.warn("Not found language ${extension.language} for InlayHintsProvider \"${extension.providerId}\"")
      } else {
        languages.add(language)
      }
    }
    return languages
  }

  override fun getProviderInfo(language: Language, providerId: String): InlayProviderInfo? {
    return getProvidersForLanguage(language).find { it.providerId == providerId }
  }
}