// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the Find popup keeps asking for more results by itself. It does so to fill the visible area, so what it must not
 * do is chain passes that cannot fill it -- each one rescans what the ones before it did, for a cap a half page larger.
 */
class FindPopupSearchStateTest {

  private val pageSize = 10

  private fun freshSearch(): FindPopupSearchState = FindPopupSearchState().apply { resetForFreshSearch(pageSize) }

  @Test
  fun `a pass that stops short of its cap has found everything`() {
    val state = freshSearch()

    state.recordPassFinished(loadMore = false, rowCount = 3, occurrences = 3, maxUsages = pageSize)

    assertTrue(state.isExhausted)
  }

  @Test
  fun `a pass that reaches its cap has more to find`() {
    val state = freshSearch()

    state.recordPassFinished(loadMore = false, rowCount = pageSize, occurrences = pageSize, maxUsages = pageSize)

    assertFalse(state.isExhausted)
    assertFalse(state.autoloadStalled)
  }

  @Test
  fun `a load-more pass that added rows is worth chaining into another`() {
    val state = freshSearch()
    state.recordPassFinished(loadMore = false, rowCount = pageSize, occurrences = pageSize, maxUsages = pageSize)

    state.beginLoadMorePass(rowCount = pageSize, pageSize = pageSize)
    state.recordPassFinished(loadMore = true, rowCount = pageSize + 4, occurrences = state.currentMaxUsages,
                             maxUsages = state.currentMaxUsages)

    assertFalse(state.autoloadStalled)
  }

  /** Every occurrence of one line is one row, so a pass that finds more of them adds nothing to show. */
  @Test
  fun `a load-more pass that added no row stops the chain`() {
    val state = freshSearch()
    state.recordPassFinished(loadMore = false, rowCount = 1, occurrences = pageSize, maxUsages = pageSize)

    state.beginLoadMorePass(rowCount = 1, pageSize = pageSize)
    state.recordPassFinished(loadMore = true, rowCount = 1, occurrences = state.currentMaxUsages,
                             maxUsages = state.currentMaxUsages)

    assertTrue(state.autoloadStalled)
    // Not exhausted: the query has more matches, and asking for them by scrolling still has to work.
    assertFalse(state.isExhausted)
  }

  @Test
  fun `a fresh search chains again`() {
    val state = freshSearch()
    state.beginLoadMorePass(rowCount = 1, pageSize = pageSize)
    state.recordPassFinished(loadMore = true, rowCount = 1, occurrences = state.currentMaxUsages,
                             maxUsages = state.currentMaxUsages)
    assertTrue(state.autoloadStalled)

    state.resetForFreshSearch(pageSize)

    assertFalse(state.autoloadStalled)
  }
}
