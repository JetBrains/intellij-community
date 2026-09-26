package com.intellij.platform.lsp.impl.features.highlightingCommon

import org.eclipse.lsp4j.Range

/**
 * The outcome of one [LspHighlightingCache.sendRequest] round trip.
 */
internal sealed interface LspPullResult<out T> {
  /**
   * A full report from the server.
   *
   * @param onAccepted runs under the cache lock right after the snapshot is committed.
   * Implementations persist per-request state there, for example the pull-diagnostics result ids.
   */
  class Full<T>(val items: List<Pair<Range, T>>, val onAccepted: (() -> Unit)? = null) : LspPullResult<T>

  /**
   * The server confirmed that the previously reported results are still valid.
   * For example, a `RelatedUnchangedDocumentDiagnosticReport` for `textDocument/diagnostic`.
   */
  class Unchanged(val onAccepted: (() -> Unit)? = null) : LspPullResult<Nothing>

  /**
   * No usable response: the server is not running, responded with an error, or timed out.
   * The cache keeps its previous state and re-requests on the next [LspHighlightingCache.getHighlightings] call.
   */
  object Failed : LspPullResult<Nothing>
}
