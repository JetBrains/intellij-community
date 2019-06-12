// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.lang.Language
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

/**
 * Factory for [InlayHintsProvider], can be used to support multiple languages with a single type of inlay hints.
 */
interface InlayHintsProviderFactory {
  fun getProvidersInfo(project: Project): List<ProviderInfo<out Any>>

  companion object {
    @JvmStatic
    val EP = ExtensionPointName<InlayHintsProviderFactory>("com.intellij.codeInsight.inlayProviderFactory")
  }
}

class ProviderInfo<T : Any>(
  val language: Language,
  val provider: InlayHintsProvider<T>
)

