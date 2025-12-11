// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.impl

import com.intellij.codeInsight.completion.CompletionResult
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.psi.codeStyle.MinusculeMatcher

open class BetterPrefixMatcher(
  private val original: PrefixMatcher,
  private val minMatchingDegree: Int,
) : PrefixMatcher(original.prefix) {
  private val humpMatcher: CamelHumpMatcher? = original as? CamelHumpMatcher

  open fun improve(result: CompletionResult): BetterPrefixMatcher {
    val degree = RealPrefixMatchingWeigher.getBestMatchingDegree(result.lookupElement, result.prefixMatcher)
    if (degree <= minMatchingDegree) return this

    return createCopy(original, degree)
  }

  protected open fun createCopy(original: PrefixMatcher, degree: Int): BetterPrefixMatcher =
    BetterPrefixMatcher(original, degree)

  override fun prefixMatches(name: String): Boolean =
    prefixMatchesEx(name) == MatchingOutcome.BETTER_MATCH

  protected open fun prefixMatchesEx(name: String): MatchingOutcome =
    if (humpMatcher != null) matchOptimized(name, humpMatcher) else matchGeneric(name)

  private fun matchGeneric(name: String): MatchingOutcome {
    return when {
      !original.prefixMatches(name) -> MatchingOutcome.NON_MATCH
      !original.isStartMatch(name) -> MatchingOutcome.WORSE_MATCH
      else -> if (original.matchingDegree(name) >= minMatchingDegree) MatchingOutcome.BETTER_MATCH else MatchingOutcome.WORSE_MATCH
    }
  }

  private fun matchOptimized(name: String?, matcher: CamelHumpMatcher): MatchingOutcome {
    val fragments = matcher.matchingFragments(name) ?: return MatchingOutcome.NON_MATCH
    return when {
      !MinusculeMatcher.isStartMatch(fragments) -> MatchingOutcome.WORSE_MATCH
      matcher.matchingDegree(name, fragments) >= minMatchingDegree -> MatchingOutcome.BETTER_MATCH
      else -> MatchingOutcome.WORSE_MATCH
    }
  }

  protected enum class MatchingOutcome {
    NON_MATCH, WORSE_MATCH, BETTER_MATCH
  }

  override fun isStartMatch(name: String): Boolean =
    original.isStartMatch(name)

  override fun matchingDegree(name: String): Int =
    original.matchingDegree(name)

  override fun cloneWithPrefix(prefix: String): PrefixMatcher =
    createCopy(original.cloneWithPrefix(prefix), minMatchingDegree)

  class AutoRestarting private constructor(
    private val result: CompletionResultSet,
    original: PrefixMatcher,
    minMatchingDegree: Int,
  ) : BetterPrefixMatcher(original, minMatchingDegree) {
    constructor(result: CompletionResultSet) : this(result, result.prefixMatcher, Int.MIN_VALUE)

    override fun createCopy(original: PrefixMatcher, degree: Int): BetterPrefixMatcher =
      AutoRestarting(result, original, degree)

    override fun prefixMatchesEx(name: String): MatchingOutcome {
      val outcome = super.prefixMatchesEx(name)
      if (outcome == MatchingOutcome.WORSE_MATCH) {
        result.restartCompletionOnAnyPrefixChange()
      }
      return outcome
    }
  }
}
