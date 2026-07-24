// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Deprecated("The old Search Everywhere is being sunset in favor of the new (Split) Search Everywhere " +
            "(com.intellij.platform.searchEverywhere). Use com.intellij.ide.actions.searcheverywhere.SplitSearchAdapter instead.")
open class SearchAdapter : SearchListenerEx {
  override fun elementsAdded(list: List<SearchEverywhereFoundElementInfo>) {}

  override fun elementsRemoved(list: List<SearchEverywhereFoundElementInfo>) {}

  override fun contributorWaits(contributor: SearchEverywhereContributor<*>) {}

  override fun contributorFinished(contributor: SearchEverywhereContributor<*>, hasMore: Boolean) {}

  override fun searchFinished(hasMoreContributors: MutableMap<SearchEverywhereContributor<*>, Boolean>) {}

  override fun searchStarted(pattern: String, contributors: MutableCollection<out SearchEverywhereContributor<*>>) {}

  override fun searchFinished(items: MutableList<Any>) {}
}