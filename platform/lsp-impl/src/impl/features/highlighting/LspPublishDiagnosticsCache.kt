package com.intellij.platform.lsp.impl.features.highlighting

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspCoroutineScopeService
import com.intellij.platform.lsp.impl.features.highlightingCommon.LspHighlightingCache
import com.intellij.platform.lsp.impl.features.highlightingCommon.LspPullResult
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

  private val pushedDiagnostics = PushedDiagnostics()

  override fun isSupportedForFile(file: VirtualFile): Boolean = true

  // The server pushes the data; scheduling a pull would only spawn a doomed no-op coroutine per read.
  override val supportsPull: Boolean get() = false

  override suspend fun sendRequest(file: VirtualFile): LspPullResult<LspDiagnosticAndLazyQuickFixes> = LspPullResult.Failed

  internal fun diagnosticsReceived(params: PublishDiagnosticsParams) {
    // the file is expected to be null if the server is dropping diagnostics for a deleted/renamed/moved file
    val lspDocument = lspClient.documentMapping.findDocumentByUrl(params.uri)
    val file: VirtualFile? = lspDocument?.fileUri?.let { lspClient.descriptor.findFileByUri(it) }
    if (file == null && params.diagnostics.isNotEmpty()) {
      // If a file has been deleted / moved / renamed, then the server sends an empty list for an unexistent file -
      // just to drop the client's cache.
      // Not empty diagnostics list for a file that doesn't exist is not expected.
      thisLogger().warn("Could not find a file with published diagnostics: ${params.uri}")
    }

    val document = file?.let { FileDocumentManager.getInstance().getCachedDocument(it) }
    val isUpToDate = file != null && params.isForCurrentVersion(file, document)
    val infosFromServer = document?.takeIf { isUpToDate }?.let {
      params.diagnostics.map { diagnostic ->
        lspDocument.toHostRange(diagnostic.range) to LspDiagnosticAndLazyQuickFixes(diagnostic, lspDocument.id)
      }
    }

    val previousFile = synchronized(this) {
      when {
        // The URI resolves to a file.
        file != null -> {
          if (infosFromServer != null) {
            // The diagnostics are stored for this file. Another file that held the URI loses it.
            pushedDiagnostics.putEvicting(params.uri, file, infosFromServer)
          }
          else {
            // The diagnostics are not stored: they are for an old version, or the file has no loaded document.
            // This file keeps its old diagnostics. Another file that held the URI loses it.
            pushedDiagnostics.evictFromOtherFile(params.uri, file)
          }
        }
        // The URI resolves to no file, for example after a rename. The file that held the URI loses it.
        else -> pushedDiagnostics.remove(params.uri)
      }
    }
    previousFile?.takeIf { it.isValid }?.let { applyDiagnostics(it, FileDocumentManager.getInstance().getCachedDocument(it)) }

    file?.takeIf { isUpToDate }?.let { applyDiagnostics(it, document) }
  }

  /**
   * Returns false when [params] are for an older version of the opened [document].
   * The server sends up-to-date diagnostics later.
   */
  private fun PublishDiagnosticsParams.isForCurrentVersion(file: VirtualFile, document: Document?): Boolean {
    val declaredVersion = version ?: return true
    // We compare versions only for opened files.
    // Assuming that some server even publishes diagnostics for unopened files,
    // those files don't have edits, so mismatched diagnostics won't be perceived as a visual mess.
    if (!lspClient.isFileOpened(file) || document == null) return true
    if (lspClient.getDocumentVersion(document) == declaredVersion) return true
    lspClient.logDebug("Ignoring diagnostics (version $declaredVersion) for ${file.name}; " +
                       "current document version: ${lspClient.getDocumentVersion(document)}")
    return false
  }

  /**
   * Builds the highlights of [file] from its entry in [pushedDiagnostics], and refreshes the editor.
   */
  private fun applyDiagnostics(file: VirtualFile, document: Document?) {
    val cachedHighlightings = if (document != null) {
      val infos = synchronized(this) { pushedDiagnostics.diagnostics(file) }
      buildHighlightings(document, infos)
    }
    else {
      // file isn't open => no editor to display diagnostics
      emptyList()
    }
    // A push cache never re-pulls, so the snapshot stamp is informational.
    applyServerHighlightings(file, document?.modificationStamp ?: 0L, cachedHighlightings)
    LspCoroutineScopeService.getInstance(project).cs.launch {
      onResponseReceived(file)
    }
  }

  override suspend fun onResponseReceived(file: VirtualFile) {
    LspHighlightingApplier.getInstance(project).scheduleHighlightingRefresh(file)
    lspClient.notifyDiagnosticsReceived(file)
  }

  override fun clearAdditionalCache() {
    pushedDiagnostics.clear()
  }
}

/**
 * The pushed diagnostics of [LspPublishDiagnosticsCache], keyed by LSP document URI and indexed by file.
 * A URI belongs to one file at a time.
 * The class is not thread-safe. The cache calls it under `synchronized(cache)`.
 */
private class PushedDiagnostics {
  private val fileToDiagnosticsByDocumentUri =
    mutableMapOf<VirtualFile, MutableMap<String, List<Pair<Range, LspDiagnosticAndLazyQuickFixes>>>>()
  private val documentUriToFile = mutableMapOf<String, VirtualFile>()

  fun diagnostics(file: VirtualFile): List<Pair<Range, LspDiagnosticAndLazyQuickFixes>> =
    fileToDiagnosticsByDocumentUri[file]?.values?.flatten().orEmpty()

  /**
   * Stores [diagnostics] of [uri] for [file].
   * Evicts [uri] from the file that held it before, if that is another file. Returns that file.
   */
  fun putEvicting(uri: String, file: VirtualFile, diagnostics: List<Pair<Range, LspDiagnosticAndLazyQuickFixes>>): VirtualFile? {
    val evictedFile = documentUriToFile.put(uri, file)?.takeIf { it != file }?.also { removeDiagnosticsFromFile(it, uri) }
    fileToDiagnosticsByDocumentUri.getOrPut(file) { mutableMapOf() }[uri] = diagnostics
    return evictedFile
  }

  /**
   * Drops [uri] if a file other than [file] holds it. Returns that other file.
   * [file] keeps its diagnostics for [uri].
   */
  fun evictFromOtherFile(uri: String, file: VirtualFile): VirtualFile? = documentUriToFile[uri]?.takeIf { it != file }?.let { remove(uri) }

  /** Drops [uri]. Returns the file that held it. */
  fun remove(uri: String): VirtualFile? = documentUriToFile.remove(uri)?.also { removeDiagnosticsFromFile(it, uri) }

  fun clear() {
    fileToDiagnosticsByDocumentUri.clear()
    documentUriToFile.clear()
  }

  private fun removeDiagnosticsFromFile(file: VirtualFile, uri: String) {
    val diagnosticsByDocumentUri = fileToDiagnosticsByDocumentUri.getValue(file)
    diagnosticsByDocumentUri.remove(uri)
    if (diagnosticsByDocumentUri.isEmpty()) fileToDiagnosticsByDocumentUri.remove(file)
  }
}
