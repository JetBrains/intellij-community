// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list.search

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

abstract class ReviewListSearchPanelViewModelBase<S : ReviewListSearchValue, Q: ReviewListQuickFilter<S>>(
  private val scope: CoroutineScope,
  private val historyModel: ReviewListSearchHistoryModel<S>,
  final override val emptySearch: S,
  final override val defaultQuickFilter: Q
) : ReviewListSearchPanelViewModel<S, Q> {

  final override val searchState = MutableStateFlow(historyModel.lastFilter ?: defaultQuickFilter.filter)

  final override val queryState = searchState.partialState(ReviewListSearchValue::searchQuery) {
    withQuery(it)
  }

  final override fun getSearchHistory(): List<S> = historyModel.getHistory()

  init {
    updateHistoryOnSearchChanges()
  }

  private fun updateHistoryOnSearchChanges() {
    scope.launch {
      searchState.collectLatestWithPrevious { old, new ->
        historyModel.lastFilter = new

        // don't persist first value
        if (old == null) {
          return@collectLatestWithPrevious
        }

        if (new.filterCount == 0 || new == defaultQuickFilter.filter) {
          return@collectLatestWithPrevious
        }

        if (old.searchQuery == new.searchQuery) {
          delay(10_000)
        }
        historyModel.add(new)
      }
    }
  }

  protected abstract fun S.withQuery(query: String?): S

  protected fun <T> MutableStateFlow<S>.partialState(getter: (S) -> T, updater: S.(T) -> S): MutableStateFlow<T> {
    val partialState = MutableStateFlow(getter(value))
    scope.launch {
      collectLatest { value ->
        partialState.update { getter(value) }
      }
    }
    scope.launch {
      partialState.collectLatest { value ->
        update { updater(it, value) }
      }
    }
    return partialState
  }

  private suspend fun <T> Flow<T>.collectLatestWithPrevious(operation: suspend (old: T?, new: T) -> Unit) {
    runningFold<T, Pair<T?, T?>>(null to null) { (_, old), new ->
      old to new
    }.collectLatest { (oldValue, newValue) ->
      if (newValue != null) {
        operation(oldValue, newValue)
      }
    }
  }
}