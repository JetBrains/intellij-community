// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.openapi.application.readAction
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.indicatorRunBlockingCancellable
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.util.Processor
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import org.jetbrains.annotations.ApiStatus

/**
 * A function that sequentially flat maps several Search Everywhere item fetch processes.
 *
 * A typical usage can be seen in [RiderGotoClassSearchEverywhereContributor] and [RiderGotoSymbolSearchEverywhereContributor].
 * There it is used to fetch items both from the IDEA contributor and the Rider backend.
 *
 * @param consumeInCancellableRead switches whether the [finalConsumer] is applied under a [runBlockingCancellable] + [readAction] or not.
 * Important for [PSIPresentationBgRendererWrapper], which implicitly requires [finalConsumer] to
 * be run in such a context.
 */
@ApiStatus.Internal
@RequiresBackgroundThread
fun fetchWeightedElementsMixing(
  pattern: String,
  progressIndicator: ProgressIndicator,
  finalConsumer: Processor<in FoundItemDescriptor<Any>>,
  vararg fetchers: (String, ProgressIndicator, Processor<in FoundItemDescriptor<Any>>) -> Unit,
) {
  val itemPool = mutableListOf<FoundItemDescriptor<Any>>()
  val adderClosure = { it: FoundItemDescriptor<Any> -> itemPool.add(it) }

  fetchers.forEach { fetcher -> fetcher(pattern, progressIndicator, adderClosure) }

  // The by-weight sorting is useless here, it is expected that the contributors return the items as-is

  @Suppress("DEPRECATION")
  indicatorRunBlockingCancellable(progressIndicator) {
    readAction { itemPool.forEach { if (!finalConsumer.process(it)) return@readAction } }
  }
}

@ApiStatus.Internal
@Suppress("UNCHECKED_CAST")
fun <T> makeTypeErasingConsumer(processor: Processor<in FoundItemDescriptor<Any>>) =
  { genericDescriptor: FoundItemDescriptor<T> -> (genericDescriptor as? FoundItemDescriptor<Any>)?.let { processor.process(it) } == true }