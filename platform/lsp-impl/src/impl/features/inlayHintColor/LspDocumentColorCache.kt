package com.intellij.platform.lsp.impl.features.inlayHintColor

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspDocumentColorSupport
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.aggregateToPullResult
import com.intellij.platform.lsp.impl.features.inlayCommon.LspInlayApplier
import com.intellij.platform.lsp.impl.features.highlightingCommon.LspHighlightingCache
import com.intellij.platform.lsp.impl.features.highlightingCommon.LspPullResult
import org.eclipse.lsp4j.Color
import org.eclipse.lsp4j.DocumentColorParams
import org.eclipse.lsp4j.Range

/**
 * [textDocument/documentColor](https://microsoft.github.io/language-server-protocol/specification/#textDocument_documentColor)
 */
internal class LspDocumentColorCache(private val lspClient: LspClientImpl) : LspHighlightingCache<Color>(lspClient.project) {
  override fun isSupportedForFile(file: VirtualFile): Boolean =
    lspClient.descriptor.lspCustomization.documentColorCustomizer is LspDocumentColorSupport &&
    lspClient.supportsDocumentColor(file)

  override suspend fun sendRequest(file: VirtualFile): LspPullResult<Color> {
    val perDocument = lspClient.documentMapping.forEachDocumentInFile(file) { lspDocument ->
      val params = DocumentColorParams(lspDocument.id)
      lspClient.sendRequest { it.textDocumentService.documentColor(params) }?.map {
        lspDocument.toHostRange(it.range) to it.color
      }
    }
    return perDocument.aggregateToPullResult()
  }

  override suspend fun onResponseReceived(file: VirtualFile) {
    // Apply directly to the editor InlayModel out-of-band instead of restarting the daemon.
    LspInlayApplier.getInstance(lspClient.project).scheduleRefresh(file)
  }
}
