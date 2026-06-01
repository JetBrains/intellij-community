// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Session-scoped paging/autoload state. One instance per [FindPopupPanel]; its lifetime
 * is "one fresh search → the next fresh search" and all of it is reset together by
 * [.resetForFreshSearch].
 */
@ApiStatus.Internal
internal class FindPopupSearchState {
  /** Per-pass emission cap; grows by pageSize/2 on each `maybeLoadMore`, reset to pageSize on every fresh search.  */
  var currentMaxUsages = 0
    private set

  // --- Exhausted -----------------------------------------------------------
  /** The last completed pass finished strictly below its cap → no more matches for this query.  */
  var isExhausted: Boolean = false

  /** Rows preserved at the head of the table from previous passes.  */
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
  var firstResultPath: String? = null

  /**
   * "The next `addRow` should clear the table." Set true at the start of every fresh search by
   * [resetForFreshSearch]; consumed via [consumeNeedReset] either by the first row that arrives
   * or by `searchStoppedProcessing` when no rows arrived at all.
   */
  private val needReset = AtomicBoolean(true)

  // --- Lifecycle -----------------------------------------------------------
  /** Reset every field for a brand-new search session (user typed a new query, scope changed, etc.).  */
  fun resetForFreshSearch(pageSize: Int) {
    currentMaxUsages = pageSize
    this.isExhausted = false
    frozenRowCount = 0
    cumulativeFilePaths.clear()
    cumulativeUsageCount = 0
    currentRowKeys.clear()
    firstResultPath = null
    needReset.set(true)
  }

  /** Atomic "the next addRow should clear the table" consume. Returns true exactly once per fresh search. */
  fun consumeNeedReset(): Boolean = needReset.compareAndSet(true, false)

  // --- Paging cap ----------------------------------------------------------
  /**
   * Mark the start of a load-more pass: pin the currently visible rows as the frozen
   * prefix and grow the per-pass emission cap by half a page.
   */
  fun beginLoadMorePass(rowCount: Int, pageSize: Int) {
    frozenRowCount = rowCount
    currentMaxUsages += (pageSize / 2)
  }

  // --- Frozen prefix / dedup ----------------------------------------------
  fun containsRowKey(key: String): Boolean {
    return currentRowKeys.contains(key)
  }

  fun recordRowKey(key: String) {
    currentRowKeys.add(key)
  }

  fun forgetRowKey(key: String) {
    currentRowKeys.remove(key)
  }

  // --- Cumulative file count ----------------------------------------------
  fun recordFilePath(path: String) {
    cumulativeFilePaths.add(path)
  }

  fun cumulativeFileCount(): Int {
    return cumulativeFilePaths.size
  }

  // --- Cumulative usage count ---------------------------------------------
  fun incrementUsageCount() {
    cumulativeUsageCount++
  }

  fun cumulativeUsageCount(): Int {
    return cumulativeUsageCount
  }
}
