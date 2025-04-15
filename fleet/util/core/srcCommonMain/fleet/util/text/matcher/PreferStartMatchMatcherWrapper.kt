// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.text.matcher

import kotlinx.collections.immutable.PersistentList

class PreferStartMatchMatcherWrapper(private val myDelegateMatcher: MinusculeMatcher) : MinusculeMatcher() {
  override val pattern: String
    get() = myDelegateMatcher.pattern

  override fun matchingFragments( name: String): PersistentList<TextRange>? {
    return myDelegateMatcher.matchingFragments(name)
  }

  override fun matchingDegree(
     name: String,
    valueStartCaseMatch: Boolean,
    fragments: PersistentList<TextRange>?
  ): Int {
    var degree = myDelegateMatcher.matchingDegree(name, valueStartCaseMatch, fragments)
    if (fragments == null || fragments.isEmpty()) return degree

    if (fragments.first().startOffset == 0) degree += START_MATCH_WEIGHT
    return degree
  }

  companion object {
    const val START_MATCH_WEIGHT: Int = 10000
  }
}
