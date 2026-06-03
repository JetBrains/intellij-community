package com.intellij.platform.lsp.impl.features.highlighting

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.customization.LspDocumentLinkSupport
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.aggregatePerDocumentResults
import com.intellij.platform.lsp.impl.features.highlightingCommon.LspHighlightingCache
import org.eclipse.lsp4j.DocumentLink
import org.eclipse.lsp4j.DocumentLinkParams
import org.eclipse.lsp4j.Range

/**
 * [textDocument/documentLink](https://microsoft.github.io/language-server-protocol/specification/#textDocument_documentLink)
 */
internal class LspDocumentLinkCache(private val lspClient: LspClientImpl) : LspHighlightingCache<LspDocumentLink>(lspClient.project) {
  override fun isSupportedForFile(file: VirtualFile): Boolean =
    lspClient.descriptor.lspCustomization.documentLinkCustomizer is LspDocumentLinkSupport &&
    lspClient.supportsDocumentLink(file)

  override suspend fun sendRequest(file: VirtualFile): List<Pair<Range, LspDocumentLink>>? {
    val perDocument = lspClient.documentMapping.forEachDocumentInFile(file) { lspDocument ->
      val params = DocumentLinkParams(lspDocument.id)
      lspClient.sendRequest { it.textDocumentService.documentLink(params) }?.map {
        lspDocument.toHostRange(it.range) to LspDocumentLink(it)
      }
    }
    return perDocument.aggregatePerDocumentResults()
  }

  override suspend fun onResponseReceived(file: VirtualFile) {
    LspHighlightingApplier.getInstance(lspClient.project).scheduleHighlightingRefresh(file)
    lspClient.notifyDocumentLinksReceived(file)
  }
}


internal class LspDocumentLink(private val initialDocumentLink: DocumentLink) {
  private var resolvedDocumentLink: DocumentLink? = null

  fun resolveDocumentLink(lspClient: LspClient) {
    if (initialDocumentLink.target == null && resolvedDocumentLink == null) {
      resolvedDocumentLink = lspClient.sendRequestSync { it.textDocumentService.documentLinkResolve(initialDocumentLink) }
                             ?: initialDocumentLink
    }
  }

  val tooltip: String? = initialDocumentLink.tooltip

  val targetUri: String?
    get() = resolvedDocumentLink?.target ?: initialDocumentLink.target
}
