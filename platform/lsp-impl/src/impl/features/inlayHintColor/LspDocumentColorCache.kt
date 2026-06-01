package com.intellij.platform.lsp.impl.features.inlayHintColor

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspDocumentColorSupport
import com.intellij.platform.lsp.impl.LspServerImpl
import com.intellij.platform.lsp.impl.LspServerManagerImpl
import com.intellij.platform.lsp.impl.aggregatePerDocumentResults
import com.intellij.platform.lsp.impl.features.highlightingCommon.LspHighlightingCache
import org.eclipse.lsp4j.Color
import org.eclipse.lsp4j.DocumentColorParams
import org.eclipse.lsp4j.Range

/**
 * [textDocument/documentColor](https://microsoft.github.io/language-server-protocol/specification/#textDocument_documentColor)
 */
internal class LspDocumentColorCache(private val lspServer: LspServerImpl) : LspHighlightingCache<Color>(lspServer.project) {
  override fun isSupportedForFile(file: VirtualFile): Boolean =
    lspServer.descriptor.lspCustomization.documentColorCustomizer is LspDocumentColorSupport &&
    lspServer.supportsDocumentColor(file)

  override suspend fun sendRequest(file: VirtualFile): List<Pair<Range, Color>>? {
    val perDocument = lspServer.documentMapping.forEachDocumentInFile(file) { lspDocument ->
      val params = DocumentColorParams(lspDocument.id)
      lspServer.sendRequest { it.textDocumentService.documentColor(params) }?.map {
        lspDocument.toHostRange(it.range) to it.color
      }
    }
    return perDocument.aggregatePerDocumentResults()
  }

  override suspend fun onResponseReceived(file: VirtualFile) {
    LspServerManagerImpl.refreshInlayHints(lspServer.project, file, this)
  }
}
