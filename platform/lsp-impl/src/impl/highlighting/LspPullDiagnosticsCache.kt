package com.intellij.platform.lsp.impl.highlighting

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspDiagnosticsSupport
import com.intellij.platform.lsp.impl.LspServerImpl
import com.intellij.platform.lsp.impl.aggregatePerDocumentResults
import com.intellij.platform.lsp.impl.highlightingCommon.LspHighlightingCache
import org.eclipse.lsp4j.DocumentDiagnosticParams
import org.eclipse.lsp4j.Range

/**
 * [textDocument/diagnostic](https://microsoft.github.io/language-server-protocol/specification/#textDocument_pullDiagnostics)
 */
internal class LspPullDiagnosticsCache(private val lspServer: LspServerImpl) : LspHighlightingCache<LspDiagnosticAndLazyQuickFixes>(
  lspServer.project,
) {

  override fun isSupportedForFile(file: VirtualFile): Boolean {
    val diagnosticsCustomizer = lspServer.descriptor.lspCustomization.diagnosticsCustomizer
    return diagnosticsCustomizer is LspDiagnosticsSupport &&
           diagnosticsCustomizer.shouldAskServerForDiagnostics(file) &&
           lspServer.supportsPullDiagnostics(file)
  }

  override suspend fun sendRequest(file: VirtualFile): List<Pair<Range, LspDiagnosticAndLazyQuickFixes>>? {
    val perDocument = lspServer.documentMapping.forEachDocumentInFile(file) { lspDocument ->
      val params = DocumentDiagnosticParams(lspDocument.id)
      val result = lspServer.sendRequest { it.textDocumentService.diagnostic(params) } ?: return@forEachDocumentInFile null

      if (!result.isRelatedFullDocumentDiagnosticReport) {
        lspServer.logWarn(
          "RelatedUnchangedDocumentDiagnosticReport response is not expected because DocumentDiagnosticParams.previousResultId has not been set")
        return@forEachDocumentInFile emptyList()
      }

      result.relatedFullDocumentDiagnosticReport!!.items.map {
        lspDocument.toHostRange(it.range) to LspDiagnosticAndLazyQuickFixes(it, lspDocument.id)
      }
    }
    return perDocument.aggregatePerDocumentResults()
  }

  override suspend fun onResponseReceived(file: VirtualFile) {
    LspHighlightingApplier.getInstance(lspServer.project).scheduleHighlightingRefresh(file)
    lspServer.notifyDiagnosticsReceived(file)
  }
}
