package com.intellij.platform.lsp.impl.features.highlighting

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspDiagnosticsSupport
import com.intellij.platform.lsp.impl.LspClientImpl
import com.intellij.platform.lsp.impl.LspDocument
import com.intellij.platform.lsp.impl.features.highlightingCommon.LspHighlightingCache
import com.intellij.platform.lsp.impl.features.highlightingCommon.LspPullResult
import kotlinx.coroutines.withTimeoutOrNull
import org.eclipse.lsp4j.DocumentDiagnosticParams
import org.eclipse.lsp4j.Range
import java.util.Collections
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * [textDocument/diagnostic](https://microsoft.github.io/language-server-protocol/specification/#textDocument_pullDiagnostics)
 */
internal class LspPullDiagnosticsCache(private val lspClient: LspClientImpl) : LspHighlightingCache<LspDiagnosticAndLazyQuickFixes>(
  lspClient.project,
) {

  /**
   * The `resultId` of the last accepted diagnostic report, keyed by the LSP document URI.
   * The next pull sends it as `DocumentDiagnosticParams.previousResultId`,
   * so the server can answer with a cheap "unchanged" report instead of a full recomputation.
   */
  private val fileToResultIds: MutableMap<VirtualFile, Map<String, String>> = Collections.synchronizedMap(mutableMapOf())

  override val quiescenceDelay: Duration get() = DIAGNOSTICS_QUIESCENCE_DELAY

  override fun isSupportedForFile(file: VirtualFile): Boolean {
    val diagnosticsCustomizer = lspClient.descriptor.lspCustomization.diagnosticsCustomizer
    return diagnosticsCustomizer is LspDiagnosticsSupport &&
           diagnosticsCustomizer.shouldAskServerForDiagnostics(file) &&
           lspClient.supportsPullDiagnostics(file)
  }

  override suspend fun sendRequest(file: VirtualFile): LspPullResult<LspDiagnosticAndLazyQuickFixes> =
    withTimeoutOrNull(PULL_DIAGNOSTICS_TIMEOUT) {
      doSendRequest(file, fileToResultIds[file] ?: emptyMap())
    } ?: LspPullResult.Failed

  private suspend fun doSendRequest(
    file: VirtualFile,
    previousResultIds: Map<String, String>,
  ): LspPullResult<LspDiagnosticAndLazyQuickFixes> {
    val newResultIds = mutableMapOf<String, String>()
    val outcomes = lspClient.documentMapping.forEachDocumentInFile(file) { lspDocument ->
      pullDocumentDiagnostics(lspDocument, previousResultIds[lspDocument.id.uri], newResultIds)
    }

    val received = outcomes.filterNotNull()
    return when {
      received.isEmpty() -> LspPullResult.Failed
      received.all { it is DocumentPullOutcome.Unchanged } ->
        LspPullResult.Unchanged(onAccepted = { fileToResultIds[file] = previousResultIds + newResultIds })
      received.any { it is DocumentPullOutcome.Unchanged } && previousResultIds.isNotEmpty() -> {
        // Mixed "full" and "unchanged" reports. This is possible only for a multi-document file, for example a notebook.
        // The cache stores one aggregated snapshot per file, so re-pull every document in full.
        // The stored result ids stay in place until the retry's report is accepted.
        // The previousResultIds.isNotEmpty() condition bounds the recursion: the retry sends no ids,
        // so a mixed answer to the retry itself (non-conforming) falls through to the full branch.
        doSendRequest(file, emptyMap())
      }
      else -> LspPullResult.Full(
        received.filterIsInstance<DocumentPullOutcome.Full>().flatMap { it.items },
        onAccepted = { fileToResultIds[file] = newResultIds },
      )
    }
  }

  private suspend fun pullDocumentDiagnostics(
    lspDocument: LspDocument,
    previousResultId: String?,
    newResultIds: MutableMap<String, String>,
  ): DocumentPullOutcome? {
    val params = DocumentDiagnosticParams(lspDocument.id).apply { this.previousResultId = previousResultId }
    val result = lspClient.sendRequest { it.textDocumentService.diagnostic(params) } ?: return null
    val uri = lspDocument.id.uri

    if (result.isRelatedUnchangedDocumentDiagnosticReport) {
      if (previousResultId == null) {
        // Non-conforming, but tolerated: the server says the current results are valid.
        // A failure here would re-pull in a loop on every trigger.
        lspClient.logWarn(
          "RelatedUnchangedDocumentDiagnosticReport response is not expected because DocumentDiagnosticParams.previousResultId has not been set")
      }
      // The @NonNull annotation on resultId is not enforced during deserialization.
      @Suppress("UNNECESSARY_SAFE_CALL")
      result.relatedUnchangedDocumentDiagnosticReport!!.resultId?.let { newResultIds[uri] = it }
      return DocumentPullOutcome.Unchanged
    }

    val report = result.relatedFullDocumentDiagnosticReport!!
    report.resultId?.let { newResultIds[uri] = it }
    return DocumentPullOutcome.Full(report.items.map {
      lspDocument.toHostRange(it.range) to LspDiagnosticAndLazyQuickFixes(it, lspDocument.id)
    })
  }

  /**
   * Handles a server-forced `workspace/diagnostic/refresh` for [file]: drops the stored result ids, so the forced
   * re-pull returns a full report, and marks the cached results stale while keeping them visible.
   */
  internal fun serverForcedRefresh(file: VirtualFile) {
    fileToResultIds.remove(file)
    invalidate(file)
  }

  override fun clearAdditionalCache() {
    fileToResultIds.clear()
  }

  override suspend fun onResponseReceived(file: VirtualFile) {
    LspHighlightingApplier.getInstance(lspClient.project).scheduleHighlightingRefresh(file)
    lspClient.notifyDiagnosticsReceived(file)
  }

  private sealed interface DocumentPullOutcome {
    object Unchanged : DocumentPullOutcome
    class Full(val items: List<Pair<Range, LspDiagnosticAndLazyQuickFixes>>) : DocumentPullOutcome
  }

  private companion object {
    /**
     * Deliberately longer than [com.intellij.platform.lsp.api.LspClient.DEFAULT_REQUEST_TIMEOUT_MS]:
     * a server that compiles the project before it answers `textDocument/diagnostic` legitimately exceeds 10 s.
     */
    private val PULL_DIAGNOSTICS_TIMEOUT: Duration = 60.seconds
  }
}
