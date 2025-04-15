// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.text.matcher

import kotlinx.collections.immutable.PersistentList

/**
 * Tells whether a string matches a specific pattern. Allows for lowercase camel-hump matching.
 * Used in navigation, code completion, speed search, etc.
 *
 *
 * Inheritors MUST override the [.matchingFragments] and [.matchingDegree] methods,
 * they are not abstract for binary compatibility.
 *
 * @see NameUtil.buildMatcher
 */
abstract class MinusculeMatcher protected constructor() : Matcher {
  abstract val pattern: String

  override fun matches(name: String): Boolean {
    return matchingFragments(name) != null
  }

  open fun matchingFragments(name: String): PersistentList<TextRange>? {
    throw UnsupportedOperationException()
  }

  open fun matchingDegree(
    name: String,
    valueStartCaseMatch: Boolean = false,
  ): Int {
    return matchingDegree(name, valueStartCaseMatch, matchingFragments(name))
  }

  open fun matchingDegree(
    name: String,
    valueStartCaseMatch: Boolean = false,
    fragments: PersistentList<TextRange>? = matchingFragments(name),
  ): Int {
    throw UnsupportedOperationException()
  }

  open fun isStartMatch(name: String): Boolean {
    val fragments = matchingFragments(name)
    return fragments != null && isStartMatch(fragments)
  }

  companion object {
    fun isStartMatch(fragments: Iterable<TextRange>): Boolean {
      val iterator = fragments.iterator()
      return !iterator.hasNext() || iterator.next().startOffset == 0
    }
  }
}
