// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.text.matcher

import kotlinx.collections.immutable.PersistentList

internal open class MatcherWithFallback(
  private val myMainMatcher: MinusculeMatcher,
  private val myFallbackMatcher: MinusculeMatcher?,
) : MinusculeMatcher() {
  override val pattern: String
    get() = myMainMatcher.pattern

  override fun matches(name: String): Boolean {
    return myMainMatcher.matches(name) ||
           myFallbackMatcher != null && myFallbackMatcher.matches(name)
  }

  override fun matchingFragments(name: String): PersistentList<TextRange>? {
    val mainRanges = myMainMatcher.matchingFragments(name)
    val useMainRanges = mainRanges != null && !mainRanges.isEmpty() || myFallbackMatcher == null
    return if (useMainRanges) mainRanges else myFallbackMatcher.matchingFragments(name)
  }

  override fun matchingDegree(name: String, valueStartCaseMatch: Boolean): Int {
    val mainRanges = myMainMatcher.matchingFragments(name)
    val useMainRanges = mainRanges != null && !mainRanges.isEmpty() || myFallbackMatcher == null

    return if (useMainRanges)
      myMainMatcher.matchingDegree(name, valueStartCaseMatch, mainRanges)
    else
      myFallbackMatcher.matchingDegree(name, valueStartCaseMatch)
  }

  override fun matchingDegree(
    name: String,
    valueStartCaseMatch: Boolean,
    fragments: PersistentList<TextRange>?,
  ): Int {
    val mainRanges = myMainMatcher.matchingFragments(name)
    val useMainRanges = mainRanges != null && !mainRanges.isEmpty() || myFallbackMatcher == null

    return if (useMainRanges)
      myMainMatcher.matchingDegree(name, valueStartCaseMatch, fragments)
    else
      myFallbackMatcher.matchingDegree(name, valueStartCaseMatch, fragments)
  }

  override fun toString(): String {
    return "MatcherWithFallback{" +
           "myMainMatcher=" + myMainMatcher +
           ", myFallbackMatcher=" + myFallbackMatcher +
           '}'
  }
}
