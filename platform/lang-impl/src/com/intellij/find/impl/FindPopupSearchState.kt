// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus

/**
 * Session-scoped paging/autoload state. One instance per [FindPopupPanel]; its lifetime
 * is "one fresh search → the next fresh search" and all of it is reset together by
 * [resetForFreshSearch].
 *
 * Threading: **this class is confined to the EDT and is intentionally not thread-safe.**
 */
@ApiStatus.Internal
internal class FindPopupSearchState {
  /** Per-pass emission cap; grows by pageSize/2 on each `maybeLoadMore`, reset to pageSize on every fresh search.  */
  @get:RequiresEdt
  var currentMaxUsages = 0
    private set

  // --- Exhausted -----------------------------------------------------------
  /** The last completed pass finished strictly below its cap → no more matches for this query.  */
  @get:RequiresEdt
  @set:RequiresEdt
  var isExhausted: Boolean = false

  /** Rows preserved at the head of the table from previous passes.  */
  @get:RequiresEdt
  var frozenRowCount = 0
    private set

  /** Cumulative file paths since the last fresh search — drives the "in N files" label across paging.  */
  private val cumulativeFilePaths: MutableSet<String> = HashSet()

  /** Cumulative usage count since the last fresh search — drives the "N matches" label across paging.  */
  private var cumulativeUsageCount: Int = 0

  /** Dedup keys (`path|line|navigationOffset`) for every row currently in the table.  */
  private val currentRowKeys: MutableSet<String> = HashSet()

  /**
   * Path of the row that arrived first in the current search session. The table's row comparator
   * pins this path at the top, so it must persist across paging passes but reset on every fresh search.
   */
  @get:RequiresEdt
  @set:RequiresEdt
  var firstResultPath: String? = null

  /**
   * "The next `addRow` should clear the table." Set true at the start of every fresh search by
   * [resetForFreshSearch]; consumed via [consumeNeedReset] either by the first row that arrives
   * or by `searchStoppedProcessing` when no rows arrived at all.
   */
  private var needReset = true

  // --- Lifecycle -----------------------------------------------------------
  /** Reset every field for a brand-new search session (user typed a new query, scope changed, etc.).  */
  @RequiresEdt
  fun resetForFreshSearch(pageSize: Int) {
    currentMaxUsages = pageSize
    this.isExhausted = false
    frozenRowCount = 0
    cumulativeFilePaths.clear()
    cumulativeUsageCount = 0
    currentRowKeys.clear()
    firstResultPath = null
    needReset = true
  }

  /** "The next addRow should clear the table" consume. Returns true exactly once per fresh search. */
  @RequiresEdt
  fun consumeNeedReset(): Boolean {
    if (!needReset) return false
    needReset = false
    return true
  }

  // --- Paging cap ----------------------------------------------------------
  /**
   * Mark the start of a load-more pass: pin the currently visible rows as the frozen
   * prefix and grow the per-pass emission cap by half a page.
   */
  @RequiresEdt
  fun beginLoadMorePass(rowCount: Int, pageSize: Int) {
    frozenRowCount = rowCount
    currentMaxUsages += (pageSize / 2)
  }

  // --- Frozen prefix / dedup ----------------------------------------------
  @RequiresEdt
  fun containsRowKey(key: String): Boolean {
    return currentRowKeys.contains(key)
  }

  @RequiresEdt
  fun recordRowKey(key: String) {
    currentRowKeys.add(key)
  }

  @RequiresEdt
  fun forgetRowKey(key: String) {
    currentRowKeys.remove(key)
  }

  // --- Cumulative file count ----------------------------------------------
  @RequiresEdt
  fun recordFilePath(path: String) {
    cumulativeFilePaths.add(path)
  }

  @RequiresEdt
  fun cumulativeFileCount(): Int {
    return cumulativeFilePaths.size
  }

  // --- Cumulative usage count ---------------------------------------------
  @RequiresEdt
  fun incrementUsageCount() {
    cumulativeUsageCount++
  }

  @RequiresEdt
  fun cumulativeUsageCount(): Int {
    return cumulativeUsageCount
  }
}
