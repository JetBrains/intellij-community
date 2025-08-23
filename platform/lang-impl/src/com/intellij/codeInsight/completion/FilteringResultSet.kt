// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.completion.group.GroupedCompletionContributor
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.patterns.ElementPattern
import com.intellij.util.Consumer
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class FilteringResultSet(
  private val base: CompletionResultSet,
  private val filter: (CompletionContributor) -> Boolean,
) : CompletionResultSet(base.prefixMatcher, base.consumer, base.contributor) {
  override fun addElement(element: LookupElement) {
    base.addElement(element)
  }

  override fun withPrefixMatcher(matcher: PrefixMatcher): CompletionResultSet {
    return FilteringResultSet(base.withPrefixMatcher(matcher), filter)
  }

  override fun withPrefixMatcher(prefix: String): CompletionResultSet {
    return FilteringResultSet(base.withPrefixMatcher(prefix), filter)
  }

  override fun withRelevanceSorter(sorter: CompletionSorter): CompletionResultSet {
    return FilteringResultSet(base.withRelevanceSorter(sorter), filter)
  }

  override fun addLookupAdvertisement(text: String) {
    base.addLookupAdvertisement(text)
  }

  override fun caseInsensitive(): CompletionResultSet {
    return FilteringResultSet(base.caseInsensitive(), filter)
  }

  override fun restartCompletionOnPrefixChange(prefixCondition: ElementPattern<String>?) {
    base.restartCompletionWhenNothingMatches()
  }

  override fun restartCompletionWhenNothingMatches() {
    base.restartCompletionWhenNothingMatches()
  }

  override fun runRemainingContributors(
    parameters: CompletionParameters,
    consumer: Consumer<in CompletionResult>,
    stop: Boolean,
    customSorter: CompletionSorter?,
  ) {
    if (GroupedCompletionContributor.isGroupEnabledInApp() &&
        (contributor as? GroupedCompletionContributor)?.groupIsEnabled(parameters) == true) {
      return
    }

    if (stop) {
      stopHere()
    }
    val batchConsumer = object : BatchConsumer<CompletionResult?> {
      override fun startBatch() {
        this@FilteringResultSet.startBatch()
      }

      override fun endBatch() {
        this@FilteringResultSet.endBatch()
      }

      override fun consume(result: CompletionResult?) {
        consumer.consume(result)
      }
    }
    myCompletionService.getVariantsFromContributors(parameters, contributor, prefixMatcher, batchConsumer, customSorter, filter)
  }

  companion object {
    private fun CompletionService.getVariantsFromContributors(
      parameters: CompletionParameters,
      from: CompletionContributor,
      matcher: PrefixMatcher,
      consumer: Consumer<in CompletionResult?>,
      customSorter: CompletionSorter?,
      filter: (CompletionContributor) -> Boolean,
    ) {
      val contributors = CompletionContributor.forParameters(parameters)
      for (i in contributors.indexOf(from) + 1 until contributors.size) {
        ProgressManager.checkCanceled()
        val contributor = contributors[i]
        if (!filter(contributor)) {
          continue
        }
        var result: CompletionResultSet = FilteringResultSet(createResultSet(parameters, consumer, contributor, matcher), filter)
        if (customSorter != null) {
          result = result.withRelevanceSorter(customSorter)
        }
        try {
          getVariantsFromContributor(parameters, contributor, result)
        }
        catch (_: IndexNotReadyException) {
        }
        if (result.isStopped) {
          return
        }
      }
    }
  }
}