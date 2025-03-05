// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.util.Processor
import org.jetbrains.annotations.ApiStatus.Internal

interface WeightedSearchEverywhereContributor<I : Any> : SearchEverywhereContributor<I>, SearchEverywhereAsyncContributor<I> {
  @get:Internal
  override val synchronousContributor: SearchEverywhereContributor<I>
    get() = this

  fun fetchWeightedElements(
    pattern: String,
    progressIndicator: ProgressIndicator,
    consumer: Processor<in FoundItemDescriptor<I>>
  )

  override fun fetchElements(
    pattern: String,
    progressIndicator: ProgressIndicator,
    consumer: Processor<in I>
  ) {
    fetchWeightedElements(pattern, progressIndicator) { consumer.process(it.item) }
  }

  fun searchWeightedElements(
    pattern: String,
    progressIndicator: ProgressIndicator,
    elementsLimit: Int
  ): ContributorSearchResult<in FoundItemDescriptor<I>> {
    val builder: ContributorSearchResult.Builder<in FoundItemDescriptor<I>> = ContributorSearchResult.builder()
    fetchWeightedElements(pattern, progressIndicator) { descriptor: FoundItemDescriptor<I> ->
      if (elementsLimit < 0 || builder.itemsCount() < elementsLimit) {
        builder.addItem(descriptor)
        return@fetchWeightedElements true
      }
      else {
        builder.setHasMore(true)
        return@fetchWeightedElements false
      }
    }

    return builder.build()
  }

  fun searchWeightedElements(
    pattern: String,
    progressIndicator: ProgressIndicator
  ): List<FoundItemDescriptor<I>> {
    val result = ArrayList<FoundItemDescriptor<I>>()
    fetchWeightedElements(pattern, progressIndicator) { result.add(it) }
    return result
  }
}
