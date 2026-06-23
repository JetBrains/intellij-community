// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.api

import com.intellij.ide.plugins.PageContainer
import com.intellij.ide.plugins.marketplace.PluginReviewComment
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@Serializable
@ApiStatus.Internal
class ReviewsPageContainer private constructor(
  val currentPage: Int,
  val hasNextPage: Boolean,
  val items: List<PluginReviewComment>,
) {

  fun getNextPage(): Int {
    return currentPage + 1
  }

  companion object {
    /**
     * [com.intellij.ide.plugins.marketplace.utils.MarketplaceUrls.getPluginReviewsUrl]
     */
    private const val DEFAULT_MARKETPLACE_PAGE_SIDE = 20

    fun fromPageContainer(container: PageContainer<PluginReviewComment>): ReviewsPageContainer {
      return ReviewsPageContainer(container.currentPage, container.hasNextPage(), container.items)
    }

    fun firstPage(items: List<PluginReviewComment>): ReviewsPageContainer {
      val hasNextPage = items.size >= DEFAULT_MARKETPLACE_PAGE_SIDE
      return ReviewsPageContainer(1, hasNextPage, items)
    }

    fun withNextPage(pageContainer: ReviewsPageContainer, itemsToAdd: List<PluginReviewComment>): ReviewsPageContainer {
      val hasNextPage = itemsToAdd.size >= DEFAULT_MARKETPLACE_PAGE_SIDE
      return ReviewsPageContainer(currentPage = pageContainer.currentPage + 1,
                                  hasNextPage = hasNextPage,
                                  items = pageContainer.items + itemsToAdd)
    }

  }
}