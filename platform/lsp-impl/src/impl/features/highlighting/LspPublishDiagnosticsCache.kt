package com.intellij.platform.lsp.impl.features.highlighting

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspCoroutineScopeService
import com.intellij.platform.lsp.impl.LspDocument
import com.intellij.platform.lsp.impl.documentMapping
import com.intellij.platform.lsp.impl.features.highlightingCommon.LspHighlightingCache
import com.intellij.psi.util.PsiModificationTracker
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range

/**
 * [textDocument/publishDiagnostics](https://microsoft.github.io/language-server-protocol/specification/#textDocument_publishDiagnostics)
 *
 * Odd one out: this cache is populated by unsolicited server notifications, not by pull requests.
 * The pull hooks are overridden to no-ops, and data arrives via [diagnosticsReceived] → [applyServerHighlightings].
 */
internal class LspPublishDiagnosticsCache(
  private val lspClient: LspClientImpl,
) : LspHighlightingCache<LspDiagnosticAndLazyQuickFixes>(lspClient.project) {

  private val fileToDiagnosticsByDocumentUri =
    mutableMapOf<VirtualFile, MutableMap<String, List<Pair<Range, LspDiagnosticAndLazyQuickFixes>>>>()

  override fun isSupportedForFile(file: VirtualFile): Boolean = true

  override suspend fun sendRequest(file: VirtualFile): List<Pair<Range, LspDiagnosticAndLazyQuickFixes>>? = null

  internal fun diagnosticsReceived(params: PublishDiagnosticsParams) {
    // the file is expected to be null if the server is dropping diagnostics for a deleted/renamed/moved file
    val lspDocument = lspClient.documentMapping.findDocumentByUrl(params.uri)
    val file: VirtualFile? = lspDocument?.fileUri?.let { lspClient.descriptor.findFileByUri(it) }

    if (file == null) {
      if (params.diagnostics.isNotEmpty()) {
        // If a file has been deleted / moved / renamed, then the server sends an empty list for an unexistent file -
        // just to drop the client's cache.
        // Not empty diagnostics list for a file that doesn't exist is not expected.
        thisLogger().warn("Could not find a file with published diagnostics: ${params.uri}")
      }
      return
    }

    handleDiagnosticsNotification(params, file, lspDocument)
  }

  private fun handleDiagnosticsNotification(params: PublishDiagnosticsParams, file: VirtualFile, lspDocument: LspDocument?) {
    val document = FileDocumentManager.getInstance().getCachedDocument(file)

    val declaredVersion = params.version
    if (declaredVersion != null && lspClient.isFileOpened(file)) {
      // We compare versions only for opened files.
      // Assuming that some server even publishes diagnostics for unopened files,
      // those files don't have edits, so mismatched diagnostics won't be perceived as a visual mess.
      if (document != null && lspClient.getDocumentVersion(document) != declaredVersion) {
        // These diagnostics are for some previous document version. Ignore. The server will send up-to-date diagnostics later.
        lspClient.logDebug("Ignoring diagnostics (version $declaredVersion) for ${file.name}; " +
                           "current document version: ${lspClient.getDocumentVersion(document)}")
        return
      }
    }

    val psiModCount = PsiModificationTracker.getInstance(lspClient.project).modificationCount
    val cachedHighlightings = if (document != null) {
      val infosFromServer = params.diagnostics.map { diagnostic ->
        val hostRange = lspDocument?.toHostRange(diagnostic.range) ?: diagnostic.range
        hostRange to LspDiagnosticAndLazyQuickFixes(diagnostic, lspDocument?.id)
      }
      val allInfosFromServer = synchronized(this) {
        val diagnosticsByDocumentUri = fileToDiagnosticsByDocumentUri.getOrPut(file) { mutableMapOf() }
        diagnosticsByDocumentUri[params.uri] = infosFromServer
        diagnosticsByDocumentUri.values.flatten()
      }
      buildHighlightings(document, allInfosFromServer)
    }
    else {
      // file isn't open => no editor to display diagnostics
      emptyList()
    }

    applyServerHighlightings(file, psiModCount, cachedHighlightings)
    LspCoroutineScopeService.getInstance(project).cs.launch {
      onResponseReceived(file)
    }
  }

  override suspend fun onResponseReceived(file: VirtualFile) {
    LspHighlightingApplier.getInstance(project).scheduleHighlightingRefresh(file)
    lspClient.notifyDiagnosticsReceived(file)
  }

  override fun clearAdditionalCache() {
    fileToDiagnosticsByDocumentUri.clear()
  }
}
