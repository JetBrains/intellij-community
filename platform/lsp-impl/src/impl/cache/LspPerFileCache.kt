// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.cache

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread

/**
 * Single-slot memoization keyed by a [VirtualFile] plus a secondary key,
 * guarded by [PsiManager]'s modification count.
 *
 * A lookup hits the cache when:
 *  1. the stored file equals the queried file, and
 *  2. the global PSI modification count hasn't changed since the value was stored, and
 *  3. [matches] returns `true` for the stored key/value against the queried key.
 *
 * By default [matches] is key equality. Override it to implement containment-style lookups
 * (e.g. "cursor offset still inside the cached result's text range").
 *
 * Shape mirrors the LSP spec's `TextDocument*Params` — a file is the primary coordinate;
 * the secondary key (offset, or [Unit] for file-level requests) lives inside.
 */
internal open class LspPerFileCache<K : Any, V : Any>(
  private val project: Project,
  private val matches: (storedKey: K, storedValue: V, queriedKey: K) -> Boolean = { stored, _, queried -> stored == queried },
) : LspCache {
  private var lastPsiModificationCount: Long = -1
  private var lastFile: VirtualFile? = null
  private var lastKey: K? = null
  private var lastResult: V? = null

  @RequiresBackgroundThread
  @Synchronized
  open fun getOrCompute(file: VirtualFile, key: K, compute: () -> V?): V? {
    ProgressManager.checkCanceled()

    val psiModCount = PsiManager.getInstance(project).modificationTracker.modificationCount
    val storedFile = lastFile
    val storedKey = lastKey
    val storedValue = lastResult
    if (storedFile == file
        && storedKey != null && storedValue != null
        && lastPsiModificationCount == psiModCount
        && matches(storedKey, storedValue, key)) {
      lastKey = key
      return storedValue
    }

    val newResult = compute() ?: return null
    lastPsiModificationCount = psiModCount
    lastFile = file
    lastKey = key
    lastResult = newResult
    return newResult
  }

  @Synchronized
  override fun clearCache() {
    lastPsiModificationCount = -1
    lastFile = null
    lastKey = null
    lastResult = null
  }
}

/**
 * Convenience overload for the file-level shape — no secondary key to pass.
 */
@RequiresBackgroundThread
internal fun <V : Any> LspPerFileCache<Unit, V>.getOrCompute(file: VirtualFile, compute: () -> V?): V? =
  getOrCompute(file, Unit, compute)
