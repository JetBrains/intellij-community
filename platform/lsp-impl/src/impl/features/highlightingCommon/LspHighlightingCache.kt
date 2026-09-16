package com.intellij.platform.lsp.impl.features.highlightingCommon

import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.impl.LspCoroutineScopeService
import com.intellij.platform.lsp.impl.cache.LspCache
import com.intellij.platform.lsp.util.getRangeInDocument
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.MultiMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.Range
import org.jetbrains.annotations.TestOnly
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Helps to keep reasonable highlighting ranges for edited files until updated info arrives from the server.
 *
 * Staleness is per document. The snapshot stores the `Document.modificationStamp` that the request was
 * sent for. [getHighlightings] re-requests only when the current stamp differs, and a response is
 * accepted only when the stamp still matches. A change in another file does not touch this file's
 * cache: the server learns about that change from its own `didChange` notification, and a server-side
 * cross-file effect arrives as a server-forced refresh, which [invalidate] turns into a re-request.
 */
internal abstract class LspHighlightingCache<T>(protected val project: Project) : LspCache {
  private val fileToCachedHighlightingsSnapshot: MutableMap<VirtualFile, CachedHighlightingsSnapshot<T>> = mutableMapOf()
  private val fileToPendingEdits: MultiMap<VirtualFile, PendingEdit> = MultiMap()
  private val fileToStampWhenRequestSent: MutableMap<VirtualFile, Long> = mutableMapOf()
  private val fileToInFlightRequest: MutableMap<VirtualFile, Job> = mutableMapOf()

  /**
   * How long the requested document must stay stable before a pull is sent.
   * Rapid consecutive edits then converge on one final stamp, and the dedup guard in
   * [scheduleHighlightingsUpdate] collapses the triggers into a single server request.
   * Without the delay, an edit burst produces a send, an immediate `$/cancelRequest`, and a re-send.
   *
   * The first pull for a file (nothing cached, nothing in flight) skips the delay,
   * so the file-open latency is unaffected.
   */
  protected open val quiescenceDelay: Duration get() = LOW_PRIORITY_QUIESCENCE_DELAY

  /** Whether this cache requests data from the server. `false` for a cache fed by server notifications. */
  protected open val supportsPull: Boolean get() = true

  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  abstract fun isSupportedForFile(file: VirtualFile): Boolean

  @RequiresBackgroundThread(generateAssertion = false /* IJPL-115548 */)
  @RequiresReadLock(generateAssertion = false /* IJPL-115548 */)
  internal fun getHighlightings(file: VirtualFile): List<LspCachedHighlighting<T>> {
    if (!isSupportedForFile(file)) return emptyList()

    synchronized(this) {
      val highlightingsSnapshot = fileToCachedHighlightingsSnapshot[file]

      val docModStamp = FileDocumentManager.getInstance().getCachedDocument(file)?.modificationStamp
      if (supportsPull && docModStamp != null && highlightingsSnapshot?.docModStamp != docModStamp) {
        scheduleHighlightingsUpdate(file)
      }

      if (highlightingsSnapshot == null || highlightingsSnapshot.cachedHighlightings.isEmpty()) {
        return emptyList()
      }

      val updatedHighlightings = applyPendingEdits(file, highlightingsSnapshot.cachedHighlightings)

      fileToCachedHighlightingsSnapshot[file] = CachedHighlightingsSnapshot(highlightingsSnapshot.docModStamp, updatedHighlightings)
      fileToPendingEdits.remove(file)

      return updatedHighlightings
    }
  }

  private fun scheduleHighlightingsUpdate(file: VirtualFile) {
    LspCoroutineScopeService.getInstance(project).cs.launch {
      val docModStamp = settleRequestStamp(file) ?: return@launch // no document => nothing to highlight

      val job = coroutineContext.job
      synchronized(this@LspHighlightingCache) {
        if (fileToCachedHighlightingsSnapshot[file]?.docModStamp == docModStamp) {
          return@launch // a response for the same document version has been applied while this trigger was settling
        }
        if (fileToStampWhenRequestSent.put(file, docModStamp) == docModStamp) {
          // A request for this document version has been already sent, and its response stays
          // acceptable, because acceptance is gated by the document stamp. The daemon restarts on
          // every PSI tick, so this dedup also keeps the running request alive across a change in
          // another file. A server-forced refresh does not take this path: [invalidate] drops the
          // stamp and cancels the request.
          return@launch
        }
        // The previous in-flight request was sent for another document version: cancel it, so the server
        // stops working on it ($/cancelRequest), and its late response does not queue this request behind it.
        fileToInFlightRequest.put(file, job)?.cancel()
      }

      try {
        when (val result = sendRequest(file)) {
          is LspPullResult.Full -> responseReceived(file, docModStamp, result)
          is LspPullResult.Unchanged -> markSnapshotFresh(file, docModStamp, result)
          is LspPullResult.Failed -> {}
        }
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Exception) {
        thisLogger().warn("LSP highlighting request failed for ${file.name}", e)
      }
      finally {
        synchronized(this@LspHighlightingCache) {
          if (fileToInFlightRequest[file] === job) {
            // Still the current request for the file: release the slot and the dedup guard,
            // so the next getHighlightings() call can re-request.
            // When a newer request took over - possibly with the same document stamp after a forced
            // refresh - both entries belong to it, so leave them alone.
            fileToInFlightRequest.remove(file)
            fileToStampWhenRequestSent.remove(file, docModStamp)
          }
        }
      }
    }
  }

  /**
   * Reads this document's modification stamp and, except for the first pull, waits until the document
   * is stable across one full [quiescenceDelay].
   */
  private suspend fun settleRequestStamp(file: VirtualFile): Long? {
    var stamp = readDocModStamp(file) ?: return null

    // The override is null in production; only tests set it.
    @Suppress("TestOnlyProblems")
    val quiescence = quiescenceDelayOverride ?: quiescenceDelay
    if (quiescence > Duration.ZERO && !isFirstPullFor(file)) {
      while (true) {
        val previousStamp = stamp
        delay(quiescence)
        stamp = readDocModStamp(file) ?: return null
        if (stamp == previousStamp) break
      }
    }
    return stamp
  }

  private suspend fun readDocModStamp(file: VirtualFile): Long? = readAction {
    FileDocumentManager.getInstance().getDocument(file)?.modificationStamp
  }

  private fun isFirstPullFor(file: VirtualFile): Boolean = synchronized(this) {
    fileToCachedHighlightingsSnapshot[file] == null && fileToInFlightRequest[file] == null
  }

  protected abstract suspend fun sendRequest(file: VirtualFile): LspPullResult<T>

  /**
   * @param docModStamp the document stamp captured at the moment of sending the request to the server
   */
  private suspend fun responseReceived(file: VirtualFile, docModStamp: Long, result: LspPullResult.Full<T>) {
    val highlightings = readAction {
      val document = FileDocumentManager.getInstance().getDocument(file) ?: return@readAction null
      if (document.modificationStamp != docModStamp) {
        // This document changed while the request was in flight, so the response ranges are stale.
        scheduleHighlightingsUpdate(file)
        return@readAction null
      }
      buildHighlightings(document, result.items)
    }
    if (highlightings == null) return

    // A forced refresh may have cancelled this request after the last suspension point.
    currentCoroutineContext().ensureActive()
    applyServerHighlightings(file, docModStamp, highlightings, result.onAccepted)
    onResponseReceived(file)
  }

  /**
   * The server confirmed that the cached results are still valid (see [LspPullResult.Unchanged]).
   * Refreshes the snapshot's mod count, so the cache counts as fresh. Keeps the contents and the pending edits.
   */
  private suspend fun markSnapshotFresh(file: VirtualFile, docModStamp: Long, result: LspPullResult.Unchanged) {
    val docUnchanged = readAction {
      FileDocumentManager.getInstance().getDocument(file)?.modificationStamp == docModStamp
    }
    if (!docUnchanged) {
      // Same acceptance rule as for a full response.
      scheduleHighlightingsUpdate(file)
      return
    }

    // A forced refresh may have cancelled this request after the last suspension point.
    currentCoroutineContext().ensureActive()
    synchronized(this) {
      // An "unchanged" report without a cached snapshot cannot be trusted. Treat it as a failure.
      val snapshot = fileToCachedHighlightingsSnapshot[file] ?: return
      fileToCachedHighlightingsSnapshot[file] = CachedHighlightingsSnapshot(docModStamp, snapshot.cachedHighlightings)
      // Keep fileToPendingEdits: the kept highlightings still need the pending-edit adjustment.
      fileToStampWhenRequestSent.remove(file, docModStamp)
      result.onAccepted?.invoke()
    }
    onResponseReceived(file)
  }

  protected fun buildHighlightings(document: Document, infosFromServer: List<Pair<Range, T>>): List<LspCachedHighlighting<T>> {
    val result = ArrayList<LspCachedHighlighting<T>>(infosFromServer.size)
    for (infoFromServer in infosFromServer) {
      val textRange = getRangeInDocument(document, infoFromServer.first) ?: continue
      result.add(LspCachedHighlighting(textRange, infoFromServer.second))
    }
    return result
  }

  /**
   * Commits a snapshot for the document version [docModStamp]. The caller checks acceptance first.
   */
  protected fun applyServerHighlightings(
    file: VirtualFile,
    docModStamp: Long,
    highlightings: List<LspCachedHighlighting<T>>,
    onAccepted: (() -> Unit)? = null,
  ) {
    synchronized(this) {
      fileToCachedHighlightingsSnapshot[file] = CachedHighlightingsSnapshot(docModStamp, highlightings)
      fileToPendingEdits.remove(file)
      if (fileToStampWhenRequestSent[file] == docModStamp) {
        fileToStampWhenRequestSent.remove(file)
      }
      onAccepted?.invoke()
    }
  }

  /**
   * Called when the cache has been updated with just received information from the server.
   * Implementations may want, for example, to restart code highlighting.
   */
  protected abstract suspend fun onResponseReceived(file: VirtualFile)

  internal fun fileEdited(file: VirtualFile, e: DocumentEvent) = synchronized(this) {
    if (!fileToCachedHighlightingsSnapshot[file]?.cachedHighlightings.isNullOrEmpty()) {
      fileToPendingEdits.putValue(file, PendingEdit(e.offset, e.oldLength, e.newLength))
    }
  }

  private fun applyPendingEdits(
    file: VirtualFile,
    highlightings: List<LspCachedHighlighting<T>>,
  ): List<LspCachedHighlighting<T>> {
    val edits = fileToPendingEdits[file]
    return applyPendingEdits(highlightings, edits)
  }

  override fun clearCache() = synchronized(this) {
    fileToCachedHighlightingsSnapshot.clear()
    fileToPendingEdits.clear()
    fileToStampWhenRequestSent.clear()
    fileToInFlightRequest.values.forEach { it.cancel() }
    fileToInFlightRequest.clear()
    clearAdditionalCache()
  }

  protected open fun clearAdditionalCache() {}

  /**
   * Marks the cached results for [file] stale so the next [getHighlightings] re-requests them from the server, while
   * keeping the current results in place until the fresh ones arrive.
   *
   * Used for server-forced refreshes (e.g. `workspace/inlayHint/refresh`), where results change without a document edit
   * and the
   * [docModStamp][CachedHighlightingsSnapshot.docModStamp] staleness check would otherwise consider the cache fresh.
   * Unlike [clearCache], reactive consumers keep showing the previous results (no flicker); the refreshed results flow
   * in through the usual [onResponseReceived] path.
   */
  internal fun invalidate(file: VirtualFile) {
    synchronized(this) {
      // An in-flight request predates the refresh. Cancel it and drop the dedup guard,
      // so the forced request is actually sent, and the stale response cannot mark the snapshot fresh
      // (that would dedup the forced request away).
      fileToInFlightRequest.remove(file)?.cancel()
      fileToStampWhenRequestSent.remove(file)
      val snapshot = fileToCachedHighlightingsSnapshot[file] ?: return
      // STALE_DOC_MOD_STAMP never equals a real document stamp, so getHighlightings always re-requests.
      fileToCachedHighlightingsSnapshot[file] = CachedHighlightingsSnapshot(STALE_DOC_MOD_STAMP, snapshot.cachedHighlightings)
    }
  }


  private class CachedHighlightingsSnapshot<T>(
    /** the `Document.modificationStamp` the snapshot was built for; [STALE_DOC_MOD_STAMP] marks a forced refresh */
    val docModStamp: Long,
    val cachedHighlightings: List<LspCachedHighlighting<T>>,
  )

  companion object {
    private const val STALE_DOC_MOD_STAMP: Long = -1L

    /**
     * Diagnostics are what the user waits for, so their debounce is shorter than [LOW_PRIORITY_QUIESCENCE_DELAY].
     * The delay must still absorb the typing cadence. A fluent typist produces a keystroke every 100-200 ms,
     * so a shorter delay starts a pull at almost every keystroke and cancels it at the next one.
     * With 250 ms the pull starts at a natural pause, for example at a word boundary.
     */
    val DIAGNOSTICS_QUIESCENCE_DELAY: Duration = 250.milliseconds

    /**
     * Semantic tokens, document links, folding, code lens, inlay hints, and colors are cosmetic while
     * the user types. Hold them back until the document settles, so the server can serve `didChange`,
     * completion, and diagnostics first.
     */
    val LOW_PRIORITY_QUIESCENCE_DELAY: Duration = 300.milliseconds

    /**
     * Widens or disables the quiescence window in tests, where the production values are too
     * timing-sensitive to assert against.
     */
    @set:TestOnly
    @Volatile
    var quiescenceDelayOverride: Duration? = null
  }
}
