package com.intellij.platform.lsp.impl.features.inlayCommon

import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsProviderFactory
import com.intellij.lang.Language
import com.intellij.openapi.project.DumbAware

internal class LspInlayHintsProviderFactory : InlayHintsProviderFactory, DumbAware {
  override fun getProvidersInfoForLanguage(language: Language): List<InlayHintsProvider<out Any>> =
    listOf(LspInlayHintsProvider())
}