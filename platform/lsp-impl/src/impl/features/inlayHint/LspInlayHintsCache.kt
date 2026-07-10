package com.intellij.platform.lsp.impl.features.inlayHint

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspInlayHintSupport
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.aggregatePerDocumentResults
import com.intellij.platform.lsp.impl.features.inlayCommon.LspInlayApplier
import com.intellij.platform.lsp.impl.features.highlightingCommon.LspHighlightingCache
import org.eclipse.lsp4j.InlayHint
import org.eclipse.lsp4j.InlayHintParams
import org.eclipse.lsp4j.Range

/**
 * [textDocument/inlayHint](https://microsoft.github.io/language-server-protocol/specification/#textDocument_inlayHint)
 */
internal class LspInlayHintsCache(private val lspClient: LspClientImpl) : LspHighlightingCache<InlayHint>(lspClient.project) {
  override fun isSupportedForFile(file: VirtualFile): Boolean {
    val customizer = lspClient.descriptor.lspCustomization.inlayHintCustomizer
    return customizer is LspInlayHintSupport &&
           lspClient.supportsInlayHints(file) &&
           customizer.shouldAskServerForInlayHints(file)
  }

  override suspend fun sendRequest(file: VirtualFile): List<Pair<Range, InlayHint>>? {
    val perDocument = lspClient.documentMapping.forEachDocumentInFile(file) { lspDocument, range ->
      val params = InlayHintParams(lspDocument.id, range)
      lspClient.sendRequest { it.textDocumentService.inlayHint(params) }?.map {
        val mappedPosition = lspDocument.toHostPosition(it.position)
        Range(mappedPosition, mappedPosition) to it
      }
    }
    return perDocument.aggregatePerDocumentResults()
  }

  override suspend fun onResponseReceived(file: VirtualFile) {
    // Apply directly to the editor InlayModel out-of-band instead of restarting the daemon.
    LspInlayApplier.getInstance(lspClient.project).scheduleRefresh(file)
  }
}