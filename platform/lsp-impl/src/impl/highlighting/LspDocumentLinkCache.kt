package com.intellij.platform.lsp.impl.highlighting

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.customization.LspDocumentLinkSupport
import com.intellij.platform.lsp.impl.LspServerImpl
import com.intellij.platform.lsp.impl.aggregatePerDocumentResults
import com.intellij.platform.lsp.impl.highlightingCommon.LspHighlightingCache
import org.eclipse.lsp4j.DocumentLink
import org.eclipse.lsp4j.DocumentLinkParams
import org.eclipse.lsp4j.Range

/**
 * [textDocument/documentLink](https://microsoft.github.io/language-server-protocol/specification/#textDocument_documentLink)
 */
internal class LspDocumentLinkCache(private val lspServer: LspServerImpl) : LspHighlightingCache<LspDocumentLink>(lspServer.project) {
  override fun isSupportedForFile(file: VirtualFile): Boolean =
    lspServer.descriptor.lspCustomization.documentLinkCustomizer is LspDocumentLinkSupport &&
    lspServer.supportsDocumentLink(file)

  override suspend fun sendRequest(file: VirtualFile): List<Pair<Range, LspDocumentLink>>? {
    val perDocument = lspServer.documentMapping.forEachDocumentInFile(file) { lspDocument ->
      val params = DocumentLinkParams(lspDocument.id)
      lspServer.sendRequest { it.textDocumentService.documentLink(params) }?.map {
        lspDocument.toHostRange(it.range) to LspDocumentLink(it)
      }
    }
    return perDocument.aggregatePerDocumentResults()
  }

  override suspend fun onResponseReceived(file: VirtualFile) {
    LspHighlightingApplier.getInstance(lspServer.project).scheduleHighlightingRefresh(file)
    lspServer.notifyDocumentLinksReceived(file)
  }
}


internal class LspDocumentLink(private val initialDocumentLink: DocumentLink) {
  private var resolvedDocumentLink: DocumentLink? = null

  fun resolveDocumentLink(lspServer: LspServer) {
    if (initialDocumentLink.target == null && resolvedDocumentLink == null) {
      resolvedDocumentLink = lspServer.sendRequestSync { it.textDocumentService.documentLinkResolve(initialDocumentLink) }
                             ?: initialDocumentLink
    }
  }

  val tooltip: String? = initialDocumentLink.tooltip

  val targetUri: String?
    get() = resolvedDocumentLink?.target ?: initialDocumentLink.target
}
