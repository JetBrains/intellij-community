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
import com.intellij.psi.util.PsiModificationTracker
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
 * The cache uses two staleness checks:
 * - The global `PsiModificationTracker.modificationCount` triggers a re-request. It is sensitive to changes
 *   in every project file, so results that depend on other files stay up to date.
 * - The `Document.modificationStamp` of the requested file gates the response acceptance. A change in another
 *   file does not discard a response whose ranges are still valid for this document.
 */
internal abstract class LspHighlightingCache<T>(protected val project: Project) : LspCache {
  private val fileToCachedHighlightingsSnapshot: MutableMap<VirtualFile, CachedHighlightingsSnapshot<T>> = mutableMapOf()
  private val fileToPendingEdits: MultiMap<VirtualFile, PendingEdit> = MultiMap()
  private val fileToPsiModCountWhenRequestSent: MutableMap<VirtualFile, Long> = mutableMapOf()
  private val fileToInFlightRequest: MutableMap<VirtualFile, Job> = mutableMapOf()

  /**
   * How long the requested document must stay stable before a pull is sent.
   * Rapid consecutive triggers then converge on one final stamp, and the dedup guard in
   * [scheduleHighlightingsUpdate] collapses them into a single server request.
   * Without the delay, a trigger burst produces a send, an immediate `$/cancelRequest`, and a re-send.
   * The highlighting pass reads each pull cache more than once per daemon run.
   * Daemon restarts pile up while the user types, so such bursts are common.
   *
   * The first pull for a file (nothing cached, nothing in flight) skips the delay,
   * so the file-open latency is unaffected.
   */
  protected open val quiescenceDelay: Duration get() = LOW_PRIORITY_QUIESCENCE_DELAY

  /** Whether this cache requests data from the server. `false` for a cache fed by server notifications. */
  protected open val supportsPull: Boolean get() = true

  @RequiresReadLock
  abstract fun isSupportedForFile(file: VirtualFile): Boolean

  @RequiresBackgroundThread
  @RequiresReadLock
  internal fun getHighlightings(file: VirtualFile): List<LspCachedHighlighting<T>> {
    if (!isSupportedForFile(file)) return emptyList()

    synchronized(this) {
      val highlightingsSnapshot = fileToCachedHighlightingsSnapshot[file]

      if (supportsPull && highlightingsSnapshot?.psiModCount != PsiModificationTracker.getInstance(project).modificationCount) {
        scheduleHighlightingsUpdate(file)
      }

      if (highlightingsSnapshot == null || highlightingsSnapshot.cachedHighlightings.isEmpty()) {
        return emptyList()
      }

      val updatedHighlightings = applyPendingEdits(file, highlightingsSnapshot.cachedHighlightings)

      fileToCachedHighlightingsSnapshot[file] = CachedHighlightingsSnapshot(highlightingsSnapshot.psiModCount, updatedHighlightings)
      fileToPendingEdits.remove(file)

      return updatedHighlightings
    }
  }

  private fun scheduleHighlightingsUpdate(file: VirtualFile) {
    LspCoroutineScopeService.getInstance(project).cs.launch {
      val requestStamp = settleRequestStamp(file) ?: return@launch // no document => nothing to highlight

      val job = coroutineContext.job
      synchronized(this@LspHighlightingCache) {
        if (fileToCachedHighlightingsSnapshot[file]?.psiModCount == requestStamp.psiModCount) {
          return@launch // a response for the same PSI state has been applied while this trigger was settling
        }
        val previousModCount = fileToPsiModCountWhenRequestSent.put(file, requestStamp.psiModCount)
        if (previousModCount != null && previousModCount >= requestStamp.psiModCount) {
          fileToPsiModCountWhenRequestSent[file] = previousModCount // the same or a newer request has been already sent
          return@launch
        }
        // The previous in-flight request became obsolete: cancel it, so the server stops working on it
        // ($/cancelRequest), and its late response does not queue this request behind it.
        fileToInFlightRequest.put(file, job)?.cancel()
      }

      try {
        when (val result = sendRequest(file)) {
          is LspPullResult.Full -> responseReceived(file, requestStamp, result)
          is LspPullResult.Unchanged -> markSnapshotFresh(file, requestStamp, result)
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
            // When a newer request took over - possibly with the same PSI mod count after a forced
            // refresh - both entries belong to it, so leave them alone.
            fileToInFlightRequest.remove(file)
            fileToPsiModCountWhenRequestSent.remove(file, requestStamp.psiModCount)
          }
        }
      }
    }
  }

  /**
   * Reads the request stamp and, except for the first pull, waits until this document is stable
   * across one full [quiescenceDelay]. The document stamp is the settle key: a change in another
   * file must not defer this file's pull.
   */
  private suspend fun settleRequestStamp(file: VirtualFile): RequestStamp? {
    var stamp = readRequestStamp(file) ?: return null

    // The override is null in production; only tests set it.
    @Suppress("TestOnlyProblems")
    val quiescence = quiescenceDelayOverride ?: quiescenceDelay
    if (quiescence > Duration.ZERO && !isFirstPullFor(file)) {
      while (true) {
        val previousDocStamp = stamp.docModStamp
        delay(quiescence)
        stamp = readRequestStamp(file) ?: return null
        if (stamp.docModStamp == previousDocStamp) break
      }
    }
    return stamp
  }

  private suspend fun readRequestStamp(file: VirtualFile): RequestStamp? = readAction {
    val document = FileDocumentManager.getInstance().getDocument(file) ?: return@readAction null
    RequestStamp(
      psiModCount = PsiModificationTracker.getInstance(project).modificationCount,
      docModStamp = document.modificationStamp,
    )
  }

  private fun isFirstPullFor(file: VirtualFile): Boolean = synchronized(this) {
    fileToCachedHighlightingsSnapshot[file] == null && fileToInFlightRequest[file] == null
  }

  protected abstract suspend fun sendRequest(file: VirtualFile): LspPullResult<T>

  /**
   * @param requestStamp the state captured at the moment of sending the request to the server
   */
  private suspend fun responseReceived(file: VirtualFile, requestStamp: RequestStamp, result: LspPullResult.Full<T>) {
    val highlightings = readAction {
      val document = FileDocumentManager.getInstance().getDocument(file) ?: return@readAction null
      if (document.modificationStamp != requestStamp.docModStamp) {
        // This document changed while the request was in flight, so the response ranges are stale.
        scheduleHighlightingsUpdate(file)
        return@readAction null
      }
      buildHighlightings(document, result.items)
    }
    if (highlightings == null) return

    // A forced refresh may have cancelled this request after the last suspension point.
    currentCoroutineContext().ensureActive()
    if (!applyServerHighlightings(file, requestStamp.psiModCount, highlightings, result.onAccepted)) return
    // When another file changed while the request was in flight, the applied snapshot is already marked
    // stale (it keeps the send-time psiModCount). The next trigger re-pulls: the daemon re-runs the pass
    // for a visible editor on every PSI change. A proactive re-pull here would chain a request after
    // every response during cross-file activity.
    onResponseReceived(file)
  }

  /**
   * The server confirmed that the cached results are still valid (see [LspPullResult.Unchanged]).
   * Refreshes the snapshot's mod count, so the cache counts as fresh. Keeps the contents and the pending edits.
   */
  private suspend fun markSnapshotFresh(file: VirtualFile, requestStamp: RequestStamp, result: LspPullResult.Unchanged) {
    val docUnchanged = readAction {
      FileDocumentManager.getInstance().getDocument(file)?.modificationStamp == requestStamp.docModStamp
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
      if (snapshot.psiModCount > requestStamp.psiModCount) return // a newer response has been already applied
      fileToCachedHighlightingsSnapshot[file] = CachedHighlightingsSnapshot(requestStamp.psiModCount, snapshot.cachedHighlightings)
      // Keep fileToPendingEdits: the kept highlightings still need the pending-edit adjustment.
      fileToPsiModCountWhenRequestSent.remove(file, requestStamp.psiModCount)
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
   * @return `false` if a newer response has been already applied, so this one was skipped
   */
  protected fun applyServerHighlightings(
    file: VirtualFile,
    psiModCount: Long,
    highlightings: List<LspCachedHighlighting<T>>,
    onAccepted: (() -> Unit)? = null,
  ): Boolean {
    synchronized(this) {
      val cachedModCount = fileToCachedHighlightingsSnapshot[file]?.psiModCount
      if (cachedModCount != null && cachedModCount > psiModCount) return false
      fileToCachedHighlightingsSnapshot[file] = CachedHighlightingsSnapshot(psiModCount, highlightings)
      fileToPendingEdits.remove(file)
      fileToPsiModCountWhenRequestSent.remove(file, psiModCount)
      onAccepted?.invoke()
      return true
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
    fileToPsiModCountWhenRequestSent.clear()
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
   * [psiModCount][CachedHighlightingsSnapshot.psiModCount] staleness check would otherwise consider the cache fresh.
   * Unlike [clearCache], reactive consumers keep showing the previous results (no flicker); the refreshed results flow
   * in through the usual [onResponseReceived] path.
   */
  internal fun invalidate(file: VirtualFile) {
    synchronized(this) {
      // An in-flight request predates the refresh. Cancel it and drop the dedup guard,
      // so the forced request is actually sent, and the stale response cannot mark the snapshot fresh
      // (that would dedup the forced request away).
      fileToInFlightRequest.remove(file)?.cancel()
      fileToPsiModCountWhenRequestSent.remove(file)
      val snapshot = fileToCachedHighlightingsSnapshot[file] ?: return
      // STALE_PSI_MOD_COUNT never equals a real PsiModificationTracker count, so getHighlightings always re-requests.
      fileToCachedHighlightingsSnapshot[file] = CachedHighlightingsSnapshot(STALE_PSI_MOD_COUNT, snapshot.cachedHighlightings)
    }
  }


  private class CachedHighlightingsSnapshot<T>(
    /**
     * `PsiModificationTracker.modificationCount` at the moment of sending the request
     */
    val psiModCount: Long,
    val cachedHighlightings: List<LspCachedHighlighting<T>>,
  )

  /**
   * The state captured at the moment of sending a request to the server.
   */
  private class RequestStamp(
    /** the global `PsiModificationTracker.modificationCount`; the staleness trigger */
    val psiModCount: Long,
    /** the `Document.modificationStamp` of the requested file; the response acceptance check */
    val docModStamp: Long,
  )

  companion object {
    private const val STALE_PSI_MOD_COUNT: Long = -1L

    /**
     * Diagnostics are what the user waits for, so keep the debounce short.
     * The daemon's own auto-reparse delay already spaces the triggers.
     * These 50 ms only absorb same-burst double triggers.
     */
    val DIAGNOSTICS_QUIESCENCE_DELAY: Duration = 50.milliseconds

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
