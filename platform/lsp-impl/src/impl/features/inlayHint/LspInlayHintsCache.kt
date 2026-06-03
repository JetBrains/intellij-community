package com.intellij.platform.lsp.impl.features.inlayHint

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspInlayHintSupport
import com.intellij.platform.lsp.impl.LspServerImpl
import com.intellij.platform.lsp.impl.LspServerManagerImpl
import com.intellij.platform.lsp.impl.aggregatePerDocumentResults
import com.intellij.platform.lsp.impl.features.highlightingCommon.LspHighlightingCache
import org.eclipse.lsp4j.InlayHint
import org.eclipse.lsp4j.InlayHintParams
import org.eclipse.lsp4j.Range

/**
 * [textDocument/inlayHint](https://microsoft.github.io/language-server-protocol/specification/#textDocument_inlayHint)
 */
internal class LspInlayHintsCache(private val lspServer: LspServerImpl) : LspHighlightingCache<InlayHint>(lspServer.project) {
  override fun isSupportedForFile(file: VirtualFile): Boolean {
    val customizer = lspServer.descriptor.lspCustomization.inlayHintCustomizer
    return customizer is LspInlayHintSupport &&
           lspServer.supportsInlayHints(file) &&
           customizer.shouldAskServerForInlayHints(file)
  }

  override suspend fun sendRequest(file: VirtualFile): List<Pair<Range, InlayHint>>? {
    val perDocument = lspServer.documentMapping.forEachDocumentInFile(file) { lspDocument, range ->
      val params = InlayHintParams(lspDocument.id, range)
      lspServer.sendRequest { it.textDocumentService.inlayHint(params) }?.map {
        val mappedPosition = lspDocument.toHostPosition(it.position)
        Range(mappedPosition, mappedPosition) to it
      }
    }
    return perDocument.aggregatePerDocumentResults()
  }

  override suspend fun onResponseReceived(file: VirtualFile) {
    LspServerManagerImpl.refreshInlayHints(lspServer.project, file, this)
  }
}