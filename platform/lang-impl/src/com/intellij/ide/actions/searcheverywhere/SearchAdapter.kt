// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

open class SearchAdapter : SearchListener {
  override fun elementsAdded(list: List<SearchEverywhereFoundElementInfo>) {}

  override fun elementsRemoved(list: List<SearchEverywhereFoundElementInfo>) {}

  override fun contributorWaits(contributor: SearchEverywhereContributor<*>) {}

  override fun contributorFinished(contributor: SearchEverywhereContributor<*>, hasMore: Boolean) {}

  override fun searchFinished(hasMoreContributors: MutableMap<SearchEverywhereContributor<*>, Boolean>) {}

  override fun searchStarted(contributors: MutableCollection<out SearchEverywhereContributor<*>>) {}
}