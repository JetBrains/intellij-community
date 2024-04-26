// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ml.ngram

import com.intellij.completion.ngram.slp.counting.trie.ArrayTrieCounter
import com.intellij.completion.ngram.slp.modeling.ngram.JMModel
import com.intellij.completion.ngram.slp.modeling.ngram.NGramModel
import com.intellij.completion.ngram.slp.modeling.runners.ModelRunner

class NGramIncrementalModelRunner(private val nGramOrder: Int, val lambda: Double,
                                  model: NGramModel, vocabulary: VocabularyWithLimit) : ModelRunner(model, vocabulary) {

  companion object {
    private const val DEFAULT_LAMBDA: Double = 0.5
    private const val LAST_STORED_TOKENS: Int = 200
    private const val LAST_STORED_TOKENS_SEQUENCE: Int = 5000

    fun createNewModelRunner(order: Int, lambda: Double = DEFAULT_LAMBDA): NGramIncrementalModelRunner {
      return NGramIncrementalModelRunner(
        nGramOrder = order,
        lambda = lambda,
        model = JMModel(counter = ArrayTrieCounter(), order = order),
        vocabulary = VocabularyWithLimit(LAST_STORED_TOKENS, order, LAST_STORED_TOKENS_SEQUENCE)
      )
    }

    fun createModelRunner(order: Int,
                          lambda: Double,
                          counter: ArrayTrieCounter,
                          vocabulary: VocabularyWithLimit): NGramIncrementalModelRunner {
      return NGramIncrementalModelRunner(
        order,
        lambda,
        model = JMModel(counter = counter, order = order, lambda = lambda),
        vocabulary = vocabulary
      )
    }
  }

  init {
    assert(vocabulary.maxVocabularySize >= nGramOrder && vocabulary.recentSequence.maxSequenceLength >= nGramOrder)
  }

  internal val prevTokens: MutableList<String> = arrayListOf()

  fun learnNextToken(token: String) {
    updatePrevTokens(token)
    val vocabulary = vocabulary
    val model = model
    if (vocabulary is VocabularyWithLimit && model is NGramModel) {
      val indices = vocabulary.toIndicesWithLimit(prevTokens, model)
      if (indices.size > 1) {
        model.forget(indices.subList(0, indices.size - 1))
      }
      model.learn(indices)
    }
  }

  fun createScorer(): NGramModelScorer {
    val prevTokensSnapshot = prevTokens.toList()  // IDEA-343644 - Without the snapshot, subList may throw ConcurrentModificationException
    val prefix = if (prevTokensSnapshot.size > 1) prevTokensSnapshot.subList(1, prevTokensSnapshot.size).toTypedArray() else emptyArray()
    return NGramModelScorer({ scoreTokens(it) }, prefix)
  }

  private fun scoreTokens(tokens: List<String>): Double {
    val vocabulary = vocabulary
    if (vocabulary is VocabularyWithLimit) {
      val queryIndices = vocabulary.toExistingIndices(tokens)
      return model.modelToken(queryIndices, queryIndices.size - 1).first
    }
    return 0.0
  }

  private fun updatePrevTokens(token: String) {
    if (prevTokens.size < nGramOrder) {
      prevTokens.add(token)
      return
    }

    shiftTokens()
    prevTokens[prevTokens.size - 1] = token
  }

  private fun shiftTokens() {
    for (i in 0..prevTokens.size - 2) {
      prevTokens[i] = prevTokens[i + 1]
    }
  }
}

class NGramModelScorer(private val scoringFunction: (List<String>) -> Double, prefix: Array<String>) {
  private val tokens: MutableList<String> = mutableListOf(*prefix, "!placeholder!")

  fun score(value: String): Double {
    tokens[tokens.lastIndex] = value
    return scoringFunction(tokens)
  }
}