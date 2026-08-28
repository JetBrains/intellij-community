// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.cache

import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * Single-slot memoization keyed by a [VirtualFile] plus a secondary key,
 * guarded by a modification stamp.
 *
 * A lookup hits the cache when:
 *  1. the stored file equals the queried file, and
 *  2. the modification stamp hasn't changed since the value was stored, and
 *  3. [matches] returns `true` for the stored key/value against the queried key.
 *
 * The stamp is the global PSI modification count by default. When the cached results depend only on the
 * requested document (for example, `textDocument/documentSymbol`), pass
 * [invalidateOnlyOnDocumentChange]` = true` to key on the document's own modification stamp instead.
 * A change in another file then keeps the cache valid. Consumers such as the structure view,
 * the breadcrumbs, and the sticky lines then do not re-request on every project-wide PSI tick.
 *
 * By default [matches] is key equality. Override it to implement containment-style lookups
 * (e.g. "cursor offset still inside the cached result's text range").
 *
 * Shape mirrors the LSP spec's `TextDocument*Params` — a file is the primary coordinate;
 * the secondary key (offset, or [Unit] for file-level requests) lives inside.
 *
 * Concurrency: [getOrCompute] runs its compute function outside the cache monitor. Concurrent callers with
 * the same file and key join the in-flight computation instead of sending a duplicate request or parking on the monitor.
 * A waiter's [ProcessCanceledException][com.intellij.openapi.progress.ProcessCanceledException] does not
 * cancel the shared computation; the owner's cancellation releases the waiters, and one of them retries.
 */
internal open class LspPerFileCache<K : Any, V : Any>(
  private val project: Project,
  private val invalidateOnlyOnDocumentChange: Boolean = false,
  private val matches: (storedKey: K, storedValue: V, queriedKey: K) -> Boolean = { stored, _, queried -> stored == queried },
) : LspCache {

  private class Slot<K : Any, V : Any>(
    val stamp: Long,
    val file: VirtualFile,
    /** mutable: a containment-style hit re-anchors the key to the latest query */
    var key: K,
    val future: CompletableFuture<V?>,
  )

  private var slot: Slot<K, V>? = null // guarded by `this`

  @RequiresBackgroundThread
  open fun getOrCompute(file: VirtualFile, key: K, compute: () -> V?): V? {
    while (true) {
      ProgressManager.checkCanceled()

      val stamp = currentStamp(file)
      var owner = false
      var joinedSameKey = true
      val future: CompletableFuture<V?> = synchronized(this) {
        val s = slot
        if (s != null && s.file == file && s.stamp == stamp) {
          if (s.future.isDone) {
            val stored = if (s.future.isCompletedExceptionally) null else s.future.getNow(null)
            if (stored != null && matches(s.key, stored, key)) {
              s.key = key
              return stored
            }
          }
          else {
            // A computation for this file and stamp is in flight. Join it: for the same key its result is
            // the answer. For a different key the completed result may still satisfy [matches]
            // (a containment lookup), so wait and re-evaluate instead of computing concurrently.
            joinedSameKey = s.key == key
            return@synchronized s.future
          }
        }
        val freshFuture = CompletableFuture<V?>()
        slot = Slot(stamp, file, key, freshFuture)
        owner = true
        freshFuture
      }

      if (owner) {
        val result = try {
          compute()
        }
        catch (t: Throwable) {
          dropSlot(future)
          future.completeExceptionally(t) // release the waiters; one of them retries
          throw t
        }
        if (result == null) {
          dropSlot(future) // `null` is not cached; the next call recomputes
        }
        future.complete(result)
        return result
      }

      // A waiter: poll the shared future with cancellability.
      // Do NOT cancel the shared future on this waiter's cancellation - the owner's request serves the others.
      // No own timeout: the owner's compute() is bounded by the request timeout and completes the future either way.
      while (!future.isDone) {
        ProgressManager.checkCanceled()
        try {
          future.get(WAITER_POLL_MS, TimeUnit.MILLISECONDS)
          break
        }
        catch (_: TimeoutException) {
          // keep waiting
        }
        catch (_: ExecutionException) {
          break
        }
        catch (_: CancellationException) {
          break
        }
      }
      if (joinedSameKey && future.isDone && !future.isCompletedExceptionally) {
        return future.getNow(null)
      }
      // The owner failed, or this caller waited on a different key. Retry: the completed slot can serve
      // a containment hit; otherwise this caller becomes the new owner.
    }
  }

  private fun currentStamp(file: VirtualFile): Long {
    if (invalidateOnlyOnDocumentChange) {
      // Fall back to the global count when the document is not in memory:
      // bypassing the cache here would send a server request on every call.
      FileDocumentManager.getInstance().getCachedDocument(file)?.let { return it.modificationStamp }
    }
    return PsiManager.getInstance(project).modificationTracker.modificationCount
  }

  private fun dropSlot(expectedFuture: CompletableFuture<V?>) {
    synchronized(this) {
      if (slot?.future === expectedFuture) {
        slot = null
      }
    }
  }

  @Synchronized
  override fun clearCache() {
    slot = null
  }

  private companion object {
    private const val WAITER_POLL_MS = 10L
  }
}

/**
 * Convenience overload for the file-level shape — no secondary key to pass.
 */
@RequiresBackgroundThread
internal fun <V : Any> LspPerFileCache<Unit, V>.getOrCompute(file: VirtualFile, compute: () -> V?): V? =
  getOrCompute(file, Unit, compute)
