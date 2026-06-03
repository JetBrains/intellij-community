package com.intellij.platform.lsp.impl.features.codeLens

import com.intellij.codeInsight.codeVision.settings.CodeVisionGroupSettingProvider
import com.intellij.platform.lsp.api.LspBundle

internal class LspCodeVisionGroupSettingProvider : CodeVisionGroupSettingProvider {
  override val groupId: String
    get() = LSP_CODE_VISION_PROVIDER_ID

  override val groupName: String
    get() = LspBundle.message("codeLens.LspCodeVisionProvider.name")

  override val description: String
    get() = LspBundle.message("codeLens.LspCodeVisionProvider.description")
}
