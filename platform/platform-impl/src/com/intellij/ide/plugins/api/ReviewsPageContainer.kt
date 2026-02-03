// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.api

import com.intellij.ide.plugins.PageContainer
import com.intellij.ide.plugins.marketplace.PluginReviewComment
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@Serializable
@ApiStatus.Internal
class ReviewsPageContainer(val myPageSize: Int, var myCurrentPage: Int, val items: MutableList<PluginReviewComment> = mutableListOf()) {
  private var myLastPage = false

  fun addItems(itemsToAdd: List<PluginReviewComment>) {
    items.addAll(itemsToAdd)
    myCurrentPage++
    myLastPage = itemsToAdd.size < myPageSize
  }

  val isNextPage: Boolean
    get() = !myLastPage

  fun getNextPage(): Int {
    return myCurrentPage + 1
  }

  companion object {
    fun fromPageContainer(container: PageContainer<PluginReviewComment>): ReviewsPageContainer {
      return ReviewsPageContainer(container.pageSize, container.currentPage, container.items)
    }
  }
}