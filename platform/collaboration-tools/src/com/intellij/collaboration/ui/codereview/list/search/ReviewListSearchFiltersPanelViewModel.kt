// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list.search

import kotlinx.coroutines.flow.StateFlow

interface ReviewListSearchFiltersPanelViewModel<S : ReviewListSearchValue, Q : ReviewListQuickFilter<S>> {
  val quickFilters: List<Q>

  val searchTextState: StateFlow<String?>
  val searchQueryState: StateFlow<S>

  fun setSearchText(text: String)
  fun submitSearchText()
  fun setSearchQuery(query: S)

  /**
   * Reset the search query to the default value
   */
  fun resetSearchQuery()

  /**
   * Reset the search query to the empty value
   */
  fun clearSearchQuery()

  fun getSearchHistory(): List<S>
}