package com.intellij.platform.lsp.impl.features.codeLens

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspCodeLensSupport
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.aggregateToPullResult
import com.intellij.platform.lsp.impl.features.LspFeaturesRefreshing
import com.intellij.platform.lsp.impl.features.highlightingCommon.LspHighlightingCache
import com.intellij.platform.lsp.impl.features.highlightingCommon.LspPullResult
import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.CodeLensParams
import org.eclipse.lsp4j.Range

internal class LspCodeLensCache(private val lspClient: LspClientImpl) : LspHighlightingCache<CodeLens>(lspClient.project) {
  override fun isSupportedForFile(file: VirtualFile): Boolean {
    val customizer = lspClient.descriptor.lspCustomization.codeLensCustomizer
    return customizer is LspCodeLensSupport &&
           lspClient.supportsCodeLens(file) &&
           customizer.shouldAskServerForCodeLenses(file)
  }

  override suspend fun sendRequest(file: VirtualFile): LspPullResult<CodeLens> {
    val resolveSupported = lspClient.serverCapabilities?.codeLensProvider?.resolveProvider == true
    val perDocument = lspClient.documentMapping.forEachDocumentInFile(file) { lspDocument ->
      val params = CodeLensParams(lspDocument.id)
      val result = lspClient.sendRequest { it.textDocumentService.codeLens(params) } ?: return@forEachDocumentInFile null

      val lenses = if (resolveSupported) {
        result.map { lens ->
          lens.takeIf { it.command != null }
          ?: lspClient.sendRequest { it.textDocumentService.resolveCodeLens(lens) }
          ?: lens
        }
      }
      else {
        result
      }

      lenses.map { lspDocument.toHostRange(it.range) to it }
    }
    return perDocument.aggregateToPullResult()
  }

  override suspend fun onResponseReceived(file: VirtualFile) {
    LspFeaturesRefreshing.refreshCodeLenses(lspClient.project)
  }
}
