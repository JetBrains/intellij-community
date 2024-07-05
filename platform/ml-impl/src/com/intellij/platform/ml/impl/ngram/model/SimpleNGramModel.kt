// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.ngram.model

import com.intellij.platform.ml.impl.ngram.model.counter.NGramCounter
import com.intellij.platform.ml.impl.ngram.model.counter.NGramHashMapCounter
import org.jetbrains.annotations.ApiStatus


/**
 * Represents a simple n-gram language model that does not perform any smoothing.
 *
 * @param T the type of tokens in the model
 * @property ngramCounter the n-gram counter used to store the frequencies of n-grams
 * @property ngramSize the size of the n-grams used in the model
 */
@ApiStatus.Internal
class SimpleNGramModel<T> private constructor(ngramCounter: NGramCounter<T>, ngramSize: Int) : NGramModel<T>(ngramCounter, ngramSize) {

  /**
   * Score the token at [tokenIndex] in the [input].
   *
   * The scoring function is a simple Maximum Likelihood Estimation
   */
  override fun scoreToken(input: List<T>, tokenIndex: Int): Double {
    val ngram = input.toNGram(false)
    val tokenInNGramIndex = tokenIndex + 1

    return scoreNGram(ngram, tokenInNGramIndex)
  }

  private fun scoreNGram(input: NGram<out T>, tokenIndex: Int): Double {
    val minIndex = (tokenIndex - ngramSize + 1).coerceAtLeast(0)
    val inputWithToken = input.subList(minIndex, tokenIndex + 1)
    val prefix = input.subList(minIndex, tokenIndex)

    val inputFrequency = nGramCounter.getFrequencyOf(inputWithToken)
    val prefixFrequency = nGramCounter.getFrequencyOf(prefix)

    return inputFrequency.toDouble() / prefixFrequency.toDouble()
  }

  companion object {
    fun <T> train(tokens: Collection<List<T>>, ngramSize: Int): NGramModel<T> {
      return train(tokens.asSequence(), ngramSize)
    }

    private fun <T> train(tokens: Sequence<List<T>>, ngramSize: Int): NGramModel<T> {
      val ngramCounter = tokens.mapNotNull { token ->
        token.takeIf { it.isNotEmpty() }
          ?.toNGram(true)
          ?.windowed(ngramSize, partialWindows = true)  // accept partial windows, as n-grams can be smaller than expected ngramSize
          ?.filterNot {
            // If we have a partial window, then we accept it only if it's an n-gram start
            // For example, if our sentence is "I am Sam" and n-gram size is 6, then
            // <s> I am Sam </s> - we will still accept, but
            // I am Sam </s> - which would be the next window, we will not take
            it.size < ngramSize && it.first() !is NGramToken.StartOfSentence
          }
      }
        .flatten()
        .groupingBy { it }
        .eachCount()
        .let { NGramHashMapCounter(it, ngramSize) }

      return SimpleNGramModel(ngramCounter, ngramSize)
    }
  }
}