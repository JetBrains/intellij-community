// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.parameters

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.lang.Language
import com.intellij.lang.LanguageExtension
import com.intellij.lang.LanguageExtensionPoint
import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/** @see ParameterHintsExcludeListService */
@ApiStatus.Internal
interface ParameterHintsExcludeListConfigProvider {
  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<LanguageExtensionPoint<ParameterHintsExcludeListConfigProvider>> =
      ExtensionPointName.create("com.intellij.codeInsight.parameterHintsExcludeListConfigProvider");
    @JvmField
    val EP: LanguageExtension<ParameterHintsExcludeListConfigProvider> =
      LanguageExtension(EP_NAME)
  }

  fun getDefaultExcludeList(): Set<String>

  fun getExcludeListDependencyLanguage(): Language? = null

  fun isExcludeListSupported(): Boolean = true

  fun getExcludeListExplanationHtml(): @Nls String = CodeInsightBundle.message("inlay.hints.exclude.list.pattern.explanation")
}