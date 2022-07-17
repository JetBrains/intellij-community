// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.textMatching

import com.intellij.internal.ml.WordsSplitter

class SimilarityScorer(elements: List<String>, private val wordsSplitter: WordsSplitter = DEFAULT_SPLITTER) {
  private val allWords: List<List<String>> = elements.map { wordsSplitter.split(it) }

  fun score(elementText: String): List<Double> {
    val scores = mutableListOf<Double>()
    val elementWords = wordsSplitter.split(elementText)
    if (elementWords.isEmpty()) return scores
    for (words in allWords) {
      var matchedWords = 0.0
      for (word in elementWords) {
        if (words.any { it.equals(word, true) }) matchedWords++
      }
      scores.add(matchedWords / elementWords.size)
    }
    return scores
  }

  companion object {
    private val DEFAULT_SPLITTER = WordsSplitter.Builder.identifiers().withStemming().build()
  }
}
