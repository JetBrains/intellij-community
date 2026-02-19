// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.textMatching

import com.intellij.internal.ml.WordsSplitter
import com.intellij.util.text.EditDistance
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object WholeTextMatchUtil {
  private val wordsSplitter = WordsSplitter.Builder().build()
  const val baseName = "wholeText"

  /**
   * Calculates features between the whole element text and the whole query text.
   *
   * Used to identify exact matches is SE.
   *
   * @return A map containing the following features:
   *  - "levenshtein_distance":The Levenshtein distance between the element text and query text,
   *        normalized by the length of the query text.
   *  - "levenshtein_distance_case_insensitive": The Levenshtein distance between the element text and query text,
   *        normalized by the length of the query text, ignoring case.
   *  - "words_in_query": The number of words in the query text.
   *  - "words_in_element": The number of words in the element text.
   *  - "exactly_matched_words": The number of words that are exactly matched between the query text and element text.
   */
  fun calculateFeatures(elementText: String, queryText: String): Map<String, Any> {

    val levenshteinDistance = EditDistance.levenshtein(elementText, queryText, true).toDouble() / queryText.length
    val levenshteinDistanceCaseInsensitive = EditDistance.levenshtein(elementText, queryText, false).toDouble() / queryText.length
    val queryWords = wordsSplitter.split(queryText).map { it.lowercase() }
    val elementWords = wordsSplitter.split(elementText).map { it.lowercase() }
    val matchingWordsCount = queryWords.zip(elementWords).count { it.first == it.second }

    return mapOf(
      "levenshtein_distance" to levenshteinDistance,
      "levenshtein_distance_case_insensitive" to levenshteinDistanceCaseInsensitive,
      "words_in_query" to queryWords.size,
      "words_in_element" to elementWords.size,
      "exactly_matched_words" to matchingWordsCount
    )
  }
}