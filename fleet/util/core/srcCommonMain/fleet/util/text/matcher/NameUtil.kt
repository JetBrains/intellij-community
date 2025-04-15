// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.text.matcher

import fleet.util.capitalizeWithCurrentLocale
import fleet.util.text.pluralize
import kotlin.math.min

object NameUtil {
  private const val MAX_LENGTH = 40

  //heuristics: 15 can take 10-20 ms in some cases, while 10 works in 1-5 ms
  private const val TYPO_AWARE_PATTERN_LIMIT = 13

  fun nameToWordsLowerCase(name: String): List<String> {
    return NameUtilCore.nameToWords(name).map { it.lowercase() }.toList()
  }

  fun buildRegexp(pattern: String, exactPrefixLen: Int, allowToUpper: Boolean, allowToLower: Boolean): String {
    return buildRegexp(pattern, exactPrefixLen, allowToUpper, allowToLower, false, false)
  }

  fun buildRegexp(
    pattern: String,
    exactPrefixLen: Int,
    allowToUpper: Boolean,
    allowToLower: Boolean,
    lowerCaseWords: Boolean,
    forCompletion: Boolean,
  ): String {
    var pattern = pattern
    var exactPrefixLen = exactPrefixLen
    val eol = pattern.indexOf('\n')
    if (eol != -1) {
      pattern = pattern.substring(0, eol)
    }
    if (pattern.length >= MAX_LENGTH) {
      pattern = pattern.substring(0, MAX_LENGTH)
    }

    val buffer = StringBuilder()
    val endsWithSpace = !forCompletion && pattern.endsWith(' ')
    if (!forCompletion) {
      pattern = pattern.trim { it <= ' ' }
    }
    exactPrefixLen = min(exactPrefixLen.toDouble(), pattern.length.toDouble()).toInt()
    /*final boolean uppercaseOnly = containsOnlyUppercaseLetters(pattern.substring(exactPrefixLen));
    if (uppercaseOnly) {
      allowToLower = false;
    }*/
    var prevIsUppercase = false
    if (exactPrefixLen > 0) {
      val c = pattern[exactPrefixLen - 1]
      prevIsUppercase = c.isUpperCase() || c.isDigit()
    }

    for (i in 0..<exactPrefixLen) {
      val c = pattern[i]
      if (c.isLetterOrDigit()) {
        buffer.append(c)
      }
      else {
        // for standard RegExp engine
        // buffer.append("\\u");
        // buffer.append(Integer.toHexString(c + 0x20000).substring(1));

        // for OROMATCHER RegExp engine

        buffer.append("\\").append(c)
        //buffer.append(Integer.toHexString(c + 0x20000).substring(2));
      }
    }

    if (exactPrefixLen == 0) {
      buffer.append("_*") // ignore leading underscores
    }

    var firstIdentifierLetter = exactPrefixLen == 0
    var lastIsUppercase = false
    for (i in exactPrefixLen..<pattern.length) {
      val c = pattern[i]
      lastIsUppercase = false
      if (c.isLetterOrDigit()) {
        prevIsUppercase = false

        // This logic allows to use uppercase letters only to catch the name like PDM for PsiDocumentManager
        if (c.isUpperCase() || c.isDigit()) {
          prevIsUppercase = true
          lastIsUppercase = true

          buffer.append('(')

          if (!firstIdentifierLetter) {
            buffer.append("[a-z\\s0-9\\$]*")
          }

          buffer.append(c)
          if (allowToLower) {
            buffer.append('|')
            buffer.append(c.lowercaseChar())
          }
          if (!firstIdentifierLetter) {
            buffer.append("|[A-Za-z\\s0-9\\$]*[_-]+[")
            buffer.append(c)
            buffer.append(c.lowercaseChar())
            buffer.append("]")
          }
          buffer.append(')')
        }
        else if (c.isLowerCase() && allowToUpper) {
          buffer.append('[')
          buffer.append(c)
          buffer.append(c.uppercaseChar())
          buffer.append(']')
          if (lowerCaseWords) {
            buffer.append("([a-z\\s0-9\\$]*[-_]+)?")
          }
        }
        else {
          buffer.append(c)
        }

        firstIdentifierLetter = false
      }
      else if (c == '*') {
        buffer.append(".*")
        firstIdentifierLetter = true
      }
      else if (c == '.') {
        if (!firstIdentifierLetter) {
          buffer.append("[a-z\\s0-9\\$]*\\.")
        }
        else {
          buffer.append("\\.")
        }
        firstIdentifierLetter = true
      }
      else if (c == ' ') {
        buffer.append("([a-z\\s0-9\\\$_-]*[\\ _-]+)+")
        firstIdentifierLetter = true
      }
      else {
        if (c == ':' || prevIsUppercase) {
          buffer.append("[A-Za-z\\s0-9\\$]*")
        }

        firstIdentifierLetter = true

        // for standard RegExp engine
        // buffer.append("\\u");
        // buffer.append(Integer.toHexString(c + 0x20000).substring(1));

        // for OROMATCHER RegExp engine
        buffer.append("\\").append(c)
        //buffer.append(Integer.toHexString(c + 0x20000).substring(3));
      }
    }

    if (!endsWithSpace) {
      buffer.append(".*")
    }
    else if (lastIsUppercase) {
      buffer.append("[a-z\\s0-9\\$]*")
    }

    //System.out.println("rx = " + buffer.toString());
    return buffer.toString()
  }

  // needs a replacement for Character.isJavaIdentifierStart that we do not have
  //fun getSuggestionsByName(
  //  name: String,
  //  prefix: String,
  //  suffix: String,
  //  upperCaseStyle: Boolean,
  //  preferLongerNames: Boolean,
  //  isArray: Boolean,
  //): MutableList<String> {
  //  val answer = mutableListOf<String>()
  //  val words = NameUtilCore.nameToWords(name)
  //
  //  for (step in words.indices) {
  //    val wordCount = if (preferLongerNames) words.size - step else step + 1
  //
  //    val startWord = words[words.size - wordCount]
  //    val c = startWord[0]
  //    if (c == '_' || !Character.isJavaIdentifierStart(c)) {
  //      continue
  //    }
  //
  //    answer.add(compoundSuggestion(prefix, upperCaseStyle, words, wordCount, startWord, c, isArray, true) + suffix)
  //    answer.add(compoundSuggestion(prefix, upperCaseStyle, words, wordCount, startWord, c, isArray, false) + suffix)
  //  }
  //  return answer
  //}

  private fun compoundSuggestion(
    prefix: String,
    upperCaseStyle: Boolean,
    words: Array<String>,
    wordCount: Int,
    startWord: String,
    c: Char,
    isArray: Boolean,
    skip_: Boolean,
  ): String {
    var startWord = startWord
    val buffer = StringBuilder()

    buffer.append(prefix)

    if (upperCaseStyle) {
      startWord = startWord.uppercase()
    }
    else {
      if (prefix.isEmpty() || prefix.endsWith('_')) {
        startWord = startWord.lowercase()
      }
      else {
        startWord = c.uppercaseChar().toString() + startWord.substring(1)
      }
    }
    buffer.append(startWord)

    for (i in words.size - wordCount + 1..<words.size) {
      var word = words[i]
      val prevWord = words[i - 1]
      if (upperCaseStyle) {
        word = word.uppercase()
        if (prevWord[prevWord.length - 1] != '_' && word[0] != '_') {
          word = "_$word"
        }
      }
      else {
        if (prevWord[prevWord.length - 1] == '_') {
          word = word.lowercase()
        }

        if (skip_) {
          if (word == "_") continue
          if (prevWord == "_") {
            word = word.capitalizeWithCurrentLocale()
          }
        }
      }
      buffer.append(word)
    }

    var suggestion = buffer.toString()
    if (isArray) {
      suggestion = suggestion.pluralize()
      if (upperCaseStyle) {
        suggestion = suggestion.uppercase()
      }
    }
    return suggestion
  }

  fun splitNameIntoWords(name: String): Array<String> {
    return NameUtilCore.splitNameIntoWords(name)
  }

  fun nameToWords(name: String): Array<String> {
    return NameUtilCore.nameToWords(name)
  }

  fun buildMatcher(
    pattern: String,
    exactPrefixLen: Int,
    allowToUpper: Boolean,
    allowToLower: Boolean,
  ): Matcher? {
    val options = if (!allowToLower && !allowToUpper)
      MatchingCaseSensitivity.ALL
    else
      if (exactPrefixLen > 0)
        MatchingCaseSensitivity.FIRST_LETTER
      else
        MatchingCaseSensitivity.NONE
    return buildMatcher(pattern, options)
  }

  fun buildMatcher(pattern: String): MatcherBuilder {
    return MatcherBuilder(pattern)
  }

  fun buildMatcher(pattern: String, options: MatchingCaseSensitivity): MinusculeMatcher {
    return buildMatcher(pattern).withCaseSensitivity(options).build()
  }

  fun buildMatcherWithFallback(
    pattern: String,
    fallbackPattern: String,
    options: MatchingCaseSensitivity,
  ): MinusculeMatcher {
    return if (pattern == fallbackPattern)
      buildMatcher(pattern, options)
    else
      MatcherWithFallback(buildMatcher(pattern, options), buildMatcher(fallbackPattern, options))
  }

  fun capitalizeAndUnderscore(name: String): String {
    return splitWords(name, '_', String::uppercase)
  }

  fun splitWords(text: String, separator: Char, transformWord: (String) -> String): String {
    val words = NameUtilCore.nameToWords(text)
    var insertSeparator = false
    val buf = StringBuilder()
    for (word in words) {
      if (!word[0].isLetterOrDigit()) {
        buf.append(separator)
        insertSeparator = false
        continue
      }
      if (insertSeparator) {
        buf.append(separator)
      }
      else {
        insertSeparator = true
      }
      buf.append(transformWord.invoke(word))
    }
    return buf.toString()
  }

  class MatcherBuilder(private val pattern: String) {
    private var separators = ""
    private var caseSensitivity = MatchingCaseSensitivity.NONE
    private var typoTolerant = false
    private var preferStartMatches = false
    private var allOccurrences = false

    fun withCaseSensitivity(caseSensitivity: MatchingCaseSensitivity): MatcherBuilder {
      this.caseSensitivity = caseSensitivity
      return this
    }

    fun withSeparators(separators: String): MatcherBuilder {
      this.separators = separators
      return this
    }

    fun typoTolerant(): MatcherBuilder {
      this.typoTolerant = pattern.length <= TYPO_AWARE_PATTERN_LIMIT
      return this
    }

    fun preferringStartMatches(): MatcherBuilder {
      preferStartMatches = true
      return this
    }

    fun allOccurrences(): MatcherBuilder {
      allOccurrences = true
      return this
    }

    fun build(): MinusculeMatcher {
      var matcher = if (typoTolerant) FixingLayoutTypoTolerantMatcher.create(pattern, caseSensitivity, separators)
      else if (allOccurrences) AllOccurrencesMatcher.create(pattern, caseSensitivity, separators)
      else FixingLayoutMatcher(pattern, caseSensitivity, separators)
      if (preferStartMatches) {
        matcher = PreferStartMatchMatcherWrapper(matcher)
      }
      matcher = PinyinMatcher.create(matcher)
      return matcher
    }
  }

  enum class MatchingCaseSensitivity {
    NONE, FIRST_LETTER, ALL
  }
}
