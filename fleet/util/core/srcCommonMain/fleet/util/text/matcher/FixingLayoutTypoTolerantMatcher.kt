// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.text.matcher

internal object FixingLayoutTypoTolerantMatcher {
  fun create(pattern: String, options: NameUtil.MatchingCaseSensitivity?, hardSeparators: String): MinusculeMatcher {
    val mainMatcher = TypoTolerantMatcher(pattern, options, hardSeparators)
    val s = FixingLayoutMatcher.fixLayout(pattern)

    if (s != null && s != pattern) {
      val fallbackMatcher = TypoTolerantMatcher(s, options, hardSeparators)
      return MatcherWithFallback(mainMatcher, fallbackMatcher)
    }
    else {
      return mainMatcher
    }
  }
}