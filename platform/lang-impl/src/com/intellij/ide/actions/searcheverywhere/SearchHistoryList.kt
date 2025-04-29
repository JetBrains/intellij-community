// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SearchHistoryList {
  companion object {
    private const val HISTORY_LIMIT = 50
    private const val ALL_CONTRIBUTORS_GROUP_ID = "SearchEverywhereContributor.All"
  }

  private data class HistoryItem(val searchText: String, val contributorID: String)

  private val historyList = mutableListOf<HistoryItem>()

  fun getIterator(contributorID: String): HistoryIterator {
    val list = getHistoryForContributor(contributorID)
    return HistoryIterator(contributorID, list)
  }

  fun saveText(text: String, contributorID: String) {
    historyList.find { it.searchText == text && it.contributorID == contributorID }?.let {
      historyList.remove(it)
    }

    historyList.add(0, HistoryItem(text, contributorID))

    val list = filteredHistory { it.contributorID == contributorID }
    if (list.size > HISTORY_LIMIT) {
      historyList.find { it.contributorID == contributorID }?.let {
        historyList.remove(it)
      }
    }
  }

  private fun getHistoryForContributor(contributorID: String): List<String> {
    return if (contributorID == ALL_CONTRIBUTORS_GROUP_ID) {
      val entireHistory = filteredHistory { true }
      val size = entireHistory.size
      if (size > HISTORY_LIMIT) entireHistory.subList(size - HISTORY_LIMIT, size) else entireHistory
    }
    else {
      filteredHistory { it.contributorID == contributorID }
    }
  }

  private fun filteredHistory(predicate: (HistoryItem) -> Boolean): List<String> {
    return historyList
      .filter(predicate)
      .map { it.searchText }
      .distinct()
  }
}