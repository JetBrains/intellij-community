// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.text.matcher


/**
 * @author Dmitry Avdeev
 * @see NameUtil.buildMatcher
 */
internal class FixingLayoutMatcher(
  pattern: String,
  options: NameUtil.MatchingCaseSensitivity,
  hardSeparators: String
) : MatcherWithFallback(
  MinusculeMatcherImpl(pattern, options, hardSeparators),
  withFixedLayout(pattern, options, hardSeparators)
) {
  companion object {
    fun fixLayout(pattern: String): String? {
      var hasLetters = false
      var onlyWrongLetters = true
      for (i in 0..<pattern.length) {
        val c = pattern.get(i)
        if (c.isLetter()) {
          hasLetters = true
          if (c <= '\u007f') {
            onlyWrongLetters = false
            break
          }
        }
      }

      if (hasLetters && onlyWrongLetters) {
        val alternatePattern = CharArray(pattern.length)
        for (i in 0..<pattern.length) {
          val c = pattern.get(i)
          val newC = KeyboardLayoutUtil.getAsciiForChar(c)
          alternatePattern[i] = newC ?: c
        }

        return alternatePattern.concatToString()
      }
      return null
    }

    private fun withFixedLayout(
      pattern: String,
      options: NameUtil.MatchingCaseSensitivity,
      hardSeparators: String
    ): MinusculeMatcher? {
      val s: String? = fixLayout(pattern)
      if (s != null && s != pattern) {
        return MinusculeMatcherImpl(s, options, hardSeparators)
      }

      return null
    }
  }
}