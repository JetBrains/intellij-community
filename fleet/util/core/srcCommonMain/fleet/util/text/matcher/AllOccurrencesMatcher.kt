// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.text.matcher

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

/**
 * [FixingLayoutMatcher] extension that returns all matches (not just the first one)
 * from [MinusculeMatcher.matchingFragments].
 */
class AllOccurrencesMatcher private constructor(
  pattern: String,
  options: NameUtil.MatchingCaseSensitivity,
  hardSeparators: String,
) : MinusculeMatcher() {
  private val delegate: MinusculeMatcher

  init {
    delegate = FixingLayoutMatcher(pattern, options, hardSeparators)
  }

  override val pattern: String
    get() = delegate.pattern

  override fun matchingDegree(
     name: String,
    valueStartCaseMatch: Boolean, fragments: PersistentList<TextRange>?,
  ): Int {
    return delegate.matchingDegree(name, valueStartCaseMatch, fragments)
  }

  override fun matchingFragments( name: String): PersistentList<TextRange>? {
    var match = delegate.matchingFragments(name)
    if (match != null && !match.isEmpty()) {
      val allMatchesReversed = mutableListOf<PersistentList<TextRange>>()
      var lastOffset = 0
      while (match != null && !match.isEmpty()) {
        var reversedWithAbsoluteOffsets = persistentListOf<TextRange>()
        for (r in match) {
          reversedWithAbsoluteOffsets = reversedWithAbsoluteOffsets.add(0, r.shiftRight(lastOffset))
        }
        allMatchesReversed.add(reversedWithAbsoluteOffsets)
        lastOffset = reversedWithAbsoluteOffsets.get(0).endOffset
        match = delegate.matchingFragments(name.substring(lastOffset))
      }
      match = persistentListOf()
      for (i in allMatchesReversed.indices.reversed()) {
        for (range in allMatchesReversed.get(i)) {
          match = match!!.add(0, range)
        }
      }
    }
    return match
  }

  override fun toString(): String {
    return "AllOccurrencesMatcher{" +
           "delegate=" + delegate +
           '}'
  }

  companion object {
    fun create( pattern: String,  options: NameUtil.MatchingCaseSensitivity, hardSeparators: String): MinusculeMatcher {
      return AllOccurrencesMatcher(pattern, options, hardSeparators)
    }
  }
}
