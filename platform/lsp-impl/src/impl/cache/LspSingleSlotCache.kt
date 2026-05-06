// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.cache

import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread

/**
 * Single-slot memoization guarded by [PsiManager]'s modification count.
 *
 * Stores at most one `(key, value)` pair. A lookup hits the cache when:
 *  1. the global PSI modification count hasn't changed since the stored value was put, and
 *  2. [matches] returns `true` for the stored key/value against the queried key.
 *
 * By default [matches] is key equality. Override it to implement containment-style lookups
 * (e.g. "cursor offset still inside the cached result's text range").
 */
internal class LspSingleSlotCache<K : Any, V : Any>(
  private val project: Project,
  private val matches: (storedKey: K, storedValue: V, queriedKey: K) -> Boolean = { stored, _, queried -> stored == queried },
) : LspCache {
  private var lastPsiModificationCount: Long = -1
  private var lastKey: K? = null
  private var lastResult: V? = null

  @RequiresBackgroundThread
  @Synchronized
  fun getOrCompute(key: K, compute: () -> V?): V? {
    ProgressManager.checkCanceled()

    val psiModCount = PsiManager.getInstance(project).modificationTracker.modificationCount
    val storedKey = lastKey
    val storedValue = lastResult
    if (storedKey != null && storedValue != null
        && lastPsiModificationCount == psiModCount
        && matches(storedKey, storedValue, key)) {
      lastKey = key
      return storedValue
    }

    val newResult = compute() ?: return null
    lastPsiModificationCount = psiModCount
    lastKey = key
    lastResult = newResult
    return newResult
  }

  @Synchronized
  override fun clearCache() {
    lastPsiModificationCount = -1
    lastKey = null
    lastResult = null
  }
}
