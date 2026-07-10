package com.intellij.platform.lsp.impl.features.highlightingCommon

import com.intellij.openapi.application.readAction
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
import kotlinx.coroutines.launch
import org.eclipse.lsp4j.Range
import java.util.Collections

/**
 * Helps to keep reasonable highlighting ranges for edited files until updated info arrives from the server.
 */
internal abstract class LspHighlightingCache<T>(protected val project: Project) : LspCache {
  private val fileToCachedHighlightingsSnapshot: MutableMap<VirtualFile, CachedHighlightingsSnapshot<T>> = mutableMapOf()
  private val fileToPendingEdits: MultiMap<VirtualFile, PendingEdit> = MultiMap()
  private val fileToPsiModCountWhenRequestSent: MutableMap<VirtualFile, Long> = Collections.synchronizedMap(mutableMapOf())

  @RequiresReadLock
  abstract fun isSupportedForFile(file: VirtualFile): Boolean

  @RequiresBackgroundThread
  @RequiresReadLock
  internal fun getHighlightings(file: VirtualFile): List<LspCachedHighlighting<T>> {
    if (!isSupportedForFile(file)) return emptyList()

    synchronized(this) {
      val highlightingsSnapshot = fileToCachedHighlightingsSnapshot[file]

      if (highlightingsSnapshot?.psiModCount != PsiModificationTracker.getInstance(project).modificationCount) {
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
      val psiModCount = PsiModificationTracker.getInstance(project).modificationCount
      val previousModCount = fileToPsiModCountWhenRequestSent.put(file, psiModCount)
      if (psiModCount == previousModCount) return@launch // the same request has been already sent

      val infosFromServer = sendRequest(file) ?: return@launch
      responseReceived(file, psiModCount, infosFromServer)
    }
  }

  protected abstract suspend fun sendRequest(file: VirtualFile): List<Pair<Range, T>>?

  /**
   * @param psiModCount `PsiModificationTracker.modificationCount` at the moment of sending the request to the server
   */
  private suspend fun responseReceived(file: VirtualFile, psiModCount: Long, infosFromServer: List<Pair<Range, T>>) {
    val highlightings = readAction {
      if (psiModCount != PsiModificationTracker.getInstance(project).modificationCount) {
        scheduleHighlightingsUpdate(file)
        return@readAction null
      }

      val document = FileDocumentManager.getInstance().getDocument(file) ?: return@readAction null
      buildHighlightings(document, infosFromServer)
    }
    if (highlightings == null) return

    applyServerHighlightings(file, psiModCount, highlightings)
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

  protected fun applyServerHighlightings(file: VirtualFile, psiModCount: Long, highlightings: List<LspCachedHighlighting<T>>) {
    synchronized(this) {
      fileToCachedHighlightingsSnapshot[file] = CachedHighlightingsSnapshot(psiModCount, highlightings)
      fileToPendingEdits.remove(file)
      fileToPsiModCountWhenRequestSent.remove(file)
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
      val snapshot = fileToCachedHighlightingsSnapshot[file] ?: return
      // STALE_PSI_MOD_COUNT never equals a real PsiModificationTracker count, so getHighlightings always re-requests.
      fileToCachedHighlightingsSnapshot[file] = CachedHighlightingsSnapshot(STALE_PSI_MOD_COUNT, snapshot.cachedHighlightings)
      fileToPsiModCountWhenRequestSent.remove(file) // drop the dedup guard so the forced request is actually sent
    }
  }


  private class CachedHighlightingsSnapshot<T>(
    /**
     * `PsiModificationTracker.modificationCount` at the moment of sending the request
     */
    val psiModCount: Long,
    val cachedHighlightings: List<LspCachedHighlighting<T>>,
  )

  private companion object {
    private const val STALE_PSI_MOD_COUNT: Long = -1L
  }
}
