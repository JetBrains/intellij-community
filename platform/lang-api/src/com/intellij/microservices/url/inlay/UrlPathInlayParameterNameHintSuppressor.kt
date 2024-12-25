package com.intellij.microservices.url.inlay

import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.ParameterNameHintsSuppressor
import com.intellij.psi.PsiFile

private const val URL_PATH_INLAY_SEARCH_LIMIT = 3

class UrlPathInlayParameterNameHintSuppressor : ParameterNameHintsSuppressor {
  override fun isSuppressedFor(file: PsiFile, inlayInfo: InlayInfo): Boolean {
    if (!UrlPathInlayHintsProvider.isUrlPathInlaysEnabledForLanguage(file.language)) return false
    val element = file.findElementAt(inlayInfo.offset) ?: return false
    return UrlPathInlaySettingsIntention.shouldHaveUrlPathInlayAroundOffset(
      element, inlayInfo.offset, URL_PATH_INLAY_SEARCH_LIMIT
    )
  }
}