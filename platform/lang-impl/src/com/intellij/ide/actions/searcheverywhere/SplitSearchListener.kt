// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import org.jetbrains.annotations.ApiStatus
import java.util.*


@ApiStatus.Experimental
interface SplitSearchListener {
  fun searchStarted(pattern: String, tabId: String)
  fun searchFinished(count: Int)
  fun elementsAdded(uuidToElement: Map<String, Any>)
  fun elementsRemoved(count: Int) {}

  fun toSearchListener(): SearchListener = object : SearchListenerEx {
    override fun elementsAdded(list: List<SearchEverywhereFoundElementInfo>) {
      elementsAdded(list.associateBy { it.uuid ?: UUID.randomUUID().toString() })
    }

    override fun elementsRemoved(list: List<SearchEverywhereFoundElementInfo>) {
      elementsRemoved(list.size)
    }

    override fun searchFinished(hasMoreContributors: Map<SearchEverywhereContributor<*>, Boolean>) {
      searchFinished(-1)
    }

    override fun searchStarted(pattern: String, contributors: Collection<SearchEverywhereContributor<*>>) {
      searchStarted(pattern,
                    if (contributors.size > 1) SearchEverywhereManagerImpl.ALL_CONTRIBUTORS_GROUP_ID
                    else contributors.firstOrNull()?.searchProviderId ?: "")
    }

    override fun searchFinished(items: List<Any?>) {
      searchFinished(items.size)
    }

    override fun contributorWaits(contributor: SearchEverywhereContributor<*>) {}
    override fun contributorFinished(contributor: SearchEverywhereContributor<*>, hasMore: Boolean) {}
  }
}