// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion

import com.intellij.codeInsight.completion.addingPolicy.ElementsAddingPolicy
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.patterns.ElementPattern
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class PolicyObeyingResultSet(
  private val originalResult: CompletionResultSet,
  private val policyHolder: () -> ElementsAddingPolicy
) : CompletionResultSet(originalResult.prefixMatcher, originalResult.consumer, originalResult.myContributor) {

  override fun addElement(element: LookupElement) {
    policyHolder().addElement(originalResult, element)
  }

  override fun addAllElements(elements: MutableIterable<LookupElement>) {
    policyHolder().addAllElements(originalResult, elements)
  }

  override fun withPrefixMatcher(matcher: PrefixMatcher): CompletionResultSet {
    return PolicyObeyingResultSet(originalResult.withPrefixMatcher(matcher), policyHolder)
  }

  override fun withPrefixMatcher(prefix: String): CompletionResultSet {
    return PolicyObeyingResultSet(originalResult.withPrefixMatcher(prefix), policyHolder)
  }

  override fun withRelevanceSorter(sorter: CompletionSorter): CompletionResultSet {
    return PolicyObeyingResultSet(originalResult.withRelevanceSorter(sorter), policyHolder)
  }

  override fun addLookupAdvertisement(text: String) {
    originalResult.addLookupAdvertisement(text)
  }

  override fun caseInsensitive(): CompletionResultSet {
    return PolicyObeyingResultSet(originalResult.caseInsensitive(), policyHolder)
  }

  override fun restartCompletionOnPrefixChange(prefixCondition: ElementPattern<String>?) {
    originalResult.restartCompletionOnPrefixChange(prefixCondition)
  }

  override fun restartCompletionWhenNothingMatches() {
    originalResult.restartCompletionWhenNothingMatches()
  }

  override fun isStopped(): Boolean {
    return originalResult.isStopped
  }

  override fun stopHere() {
    policyHolder().onResultStop(originalResult)
    originalResult.stopHere()
  }
}