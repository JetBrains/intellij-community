// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.text.matcher

class WordPrefixMatcher(pattern: String) : Matcher {
  private val myPatternWords: Array<String> = splitToWords(pattern)
  private val myFallbackPatternWords: Array<String>?

  init {
    val fixedLayout = FixingLayoutMatcher.fixLayout(pattern)
    myFallbackPatternWords = if (fixedLayout != null && fixedLayout != pattern) NameUtil.nameToWords(fixedLayout) else null
  }

  public override fun matches(name: String): Boolean {
    val nameWords = splitToWords(name)
    return matches(myPatternWords, nameWords) || myFallbackPatternWords != null && matches(myFallbackPatternWords, nameWords)
  }

  companion object {
    private fun splitToWords(string: String): Array<String> {
      return string.split("[\\s-/]".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    }

    private fun matches(patternWords: Array<String>, nameWords: Array<String>): Boolean {
      return patternWords.all { pw -> nameWords.any { nw -> nw.startsWith(pw, ignoreCase = true) } }
    }
  }
}
