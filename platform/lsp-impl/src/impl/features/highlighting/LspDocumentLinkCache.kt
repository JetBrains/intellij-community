package com.intellij.platform.lsp.impl.features.highlighting

import com.intellij.ide.IdeBundle
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.customization.LspDocumentLinkSupport
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.aggregateToPullResult
import com.intellij.platform.lsp.impl.features.highlightingCommon.LspHighlightingCache
import com.intellij.platform.lsp.impl.features.highlightingCommon.LspPullResult
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

  override suspend fun sendRequest(file: VirtualFile): LspPullResult<LspDocumentLink> {
    val perDocument = lspClient.documentMapping.forEachDocumentInFile(file) { lspDocument ->
      val params = DocumentLinkParams(lspDocument.id)
      lspClient.sendRequest { it.textDocumentService.documentLink(params) }?.map {
        lspDocument.toHostRange(it.range) to LspDocumentLink(it)
      }
    }
    return perDocument.aggregateToPullResult()
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

  private val serverTooltip: String? = initialDocumentLink.tooltip

  /**
   * What this link tells the user it does, or `null` when the underline alone says it.
   *
   * A URL is labelled the way [WebReferenceDocumentationProvider][com.intellij.openapi.paths.WebReferenceDocumentationProvider]
   * labels a plain URL reference, rather than by repeating the URL already on screen. A file or directory gets nothing:
   * naming the gesture ("Follow link") only repeats the underline, and the target's path is long and mostly noise.
   */
  val tooltip: @NlsSafe String?
    get() {
      serverTooltip?.let { return it }

      val uri = targetUri
      @Suppress("HttpUrlsUsage")
      if (uri != null && (uri.startsWith("http://") || uri.startsWith("https://"))) {
        return IdeBundle.message("open.url.in.browser.tooltip")
      }
      return null
    }

  val targetUri: String?
    get() = resolvedDocumentLink?.target ?: initialDocumentLink.target
}
