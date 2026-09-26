// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.marketplace.statistics.features

import com.intellij.internal.statistic.eventLog.events.EventField
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import org.jetbrains.annotations.ApiStatus
import java.lang.Character.UnicodeScript

@ApiStatus.Internal
object PluginManagerUserQueryFeatureProvider {
  // whitespaces and basic punctuation symbols
  private const val WORDS_DELIMITERS = "\\s\\p{P}"
  private val WORDS_SEPARATOR_REGEXP = Regex("[$WORDS_DELIMITERS]+")
  private val WORD_CHARS_AND_DELIMITERS_REGEXP = Regex("^[\\w$WORDS_DELIMITERS]+\$")

  private val QUERY_LENGTH_DATA_KEY = EventFields.Int("queryLength")
  private val IS_EMPTY_QUERY_DATA_KEY = EventFields.Boolean("isEmptyQuery")
  private val QUERY_CONTAINS_PATH_DATA_KEY = EventFields.Boolean("queryContainsPath")
  private val QUERY_CONTAINS_SPACES_DATA_KEY = EventFields.Boolean("queryContainsSpaces")
  private val QUERY_IS_CAMEL_CASE_DATA_KEY = EventFields.Boolean("queryIsCamelCase")
  private val QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY = EventFields.Boolean("queryContainsAbbreviations")
  private val QUERY_IS_ALL_UPPERCASE_DATA_KEY = EventFields.Boolean("queryIsAllUppercase")
  private val QUERY_IS_ALL_LOWERCASE_DATA_KEY = EventFields.Boolean("queryIsAllLowercase")
  private val NUMBER_OF_WORDS_DATA_KEY = EventFields.Int("wordsNumber")
  private val WORD_CHARS_AND_DELIMITERS_ONLY_DATA_KEY = EventFields.Boolean("wordCharsAndDelimitersOnly")
  private val CONTAINS_HAN_SYMBOLS = EventFields.Boolean("withHANSymbols")

  fun getFeaturesDefinition(): Array<EventField<*>> {
    return arrayOf(
      QUERY_LENGTH_DATA_KEY, IS_EMPTY_QUERY_DATA_KEY, QUERY_CONTAINS_PATH_DATA_KEY, QUERY_CONTAINS_SPACES_DATA_KEY,
      QUERY_IS_CAMEL_CASE_DATA_KEY, QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY, QUERY_IS_ALL_UPPERCASE_DATA_KEY,
      QUERY_IS_ALL_LOWERCASE_DATA_KEY, NUMBER_OF_WORDS_DATA_KEY, WORD_CHARS_AND_DELIMITERS_ONLY_DATA_KEY, CONTAINS_HAN_SYMBOLS
    )
  }

  fun getSearchStateFeatures(userQuery: String?): List<EventPair<*>> {
    val query = userQuery ?: ""
    return listOf(
      QUERY_LENGTH_DATA_KEY.with(query.length),
      IS_EMPTY_QUERY_DATA_KEY.with(query.isEmpty()),
      QUERY_CONTAINS_PATH_DATA_KEY.with(query.indexOfLast { it == '/' || it == '\\' } in 1 until query.lastIndex),
      QUERY_CONTAINS_SPACES_DATA_KEY.with(query.contains(" ")),
      QUERY_IS_CAMEL_CASE_DATA_KEY.with(query.isCamelCase()),
      QUERY_CONTAINS_ABBREVIATIONS_DATA_KEY.with(query.containsAbbreviations()),
      QUERY_IS_ALL_UPPERCASE_DATA_KEY.with(query.all { it.isUpperCase() }),
      QUERY_IS_ALL_LOWERCASE_DATA_KEY.with(query.all { it.isLowerCase() }),
      NUMBER_OF_WORDS_DATA_KEY.with(query.wordsCount()),
      WORD_CHARS_AND_DELIMITERS_ONLY_DATA_KEY.with(query.matches(WORD_CHARS_AND_DELIMITERS_REGEXP)),
      CONTAINS_HAN_SYMBOLS.with(query.containsHANSymbols())
    )
  }

  private fun CharSequence.containsHANSymbols() = this.codePoints().anyMatch { UnicodeScript.of(it) == UnicodeScript.HAN }

  private fun CharSequence.isCamelCase(): Boolean {
    this.forEachIndexed { index, c ->
      if (index == 0) return@forEachIndexed
      // Check if there's a case change between this character and the previous one
      if (c.isUpperCase() != this[index - 1].isUpperCase()) return true
    }

    return false
  }

  private fun CharSequence.wordsCount(): Int {
    return split(WORDS_SEPARATOR_REGEXP).count { it.isNotBlank() }
  }

  private fun CharSequence.containsAbbreviations(): Boolean = this.filter { it.isLetter() }.all { it.isUpperCase() }
}