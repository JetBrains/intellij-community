package com.intellij.platform.lsp.impl.features.codeLens

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspCodeLensSupport
import com.intellij.platform.lsp.impl.LspServerImpl
import com.intellij.platform.lsp.impl.aggregatePerDocumentResults
import com.intellij.platform.lsp.impl.features.LspFeaturesRefreshing
import com.intellij.platform.lsp.impl.features.highlightingCommon.LspHighlightingCache
import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.CodeLensParams
import org.eclipse.lsp4j.Range

internal class LspCodeLensCache(private val lspServer: LspServerImpl) : LspHighlightingCache<CodeLens>(lspServer.project) {
  override fun isSupportedForFile(file: VirtualFile): Boolean {
    val customizer = lspServer.descriptor.lspCustomization.codeLensCustomizer
    return customizer is LspCodeLensSupport &&
           lspServer.supportsCodeLens(file) &&
           customizer.shouldAskServerForCodeLenses(file)
  }

  override suspend fun sendRequest(file: VirtualFile): List<Pair<Range, CodeLens>>? {
    val resolveSupported = lspServer.serverCapabilities?.codeLensProvider?.resolveProvider == true
    val perDocument = lspServer.documentMapping.forEachDocumentInFile(file) { lspDocument ->
      val params = CodeLensParams(lspDocument.id)
      val result = lspServer.sendRequest { it.textDocumentService.codeLens(params) } ?: return@forEachDocumentInFile null

      val lenses = if (resolveSupported) {
        result.map { lens ->
          lens.takeIf { it.command != null }
          ?: lspServer.sendRequest { it.textDocumentService.resolveCodeLens(lens) }
          ?: lens
        }
      }
      else {
        result
      }

      lenses.map { lspDocument.toHostRange(it.range) to it }
    }
    return perDocument.aggregatePerDocumentResults()
  }

  override suspend fun onResponseReceived(file: VirtualFile) {
    LspFeaturesRefreshing.refreshCodeLenses(lspServer.project)
  }
}
