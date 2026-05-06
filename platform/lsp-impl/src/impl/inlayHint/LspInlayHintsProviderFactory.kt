package com.intellij.platform.lsp.impl.inlayHint

import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsProviderFactory
import com.intellij.lang.Language
import com.intellij.openapi.project.DumbAware
import com.intellij.platform.lsp.impl.inlayHintColor.LspColorInlayHintsProvider

internal class LspInlayHintsProviderFactory : InlayHintsProviderFactory, DumbAware {
  override fun getProvidersInfoForLanguage(language: Language): List<InlayHintsProvider<out Any>> =
    listOf(LspColorInlayHintsProvider(), LspInlayHintsProvider())
}