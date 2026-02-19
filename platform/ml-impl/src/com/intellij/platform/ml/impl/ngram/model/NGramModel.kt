// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.ngram.model

import com.intellij.platform.ml.impl.ngram.model.counter.NGramCounter
import org.jetbrains.annotations.ApiStatus


internal typealias NGram<T> = List<NGramToken<T>>

/**
 * Every NGram internally stores NGramToken's which can have one of 3 implementations
 * - [NGramToken.StartOfSentence] object that represents start of an n-gram
 * - [NGramToken.Word] class that stores any type [T]
 * - [NGramToken.EndOfSentence] object that represents end of an n-gram
 */
@ApiStatus.Internal
sealed interface NGramToken<T> {
  object StartOfSentence : NGramToken<Nothing> {
    override fun toString(): String = "<S>"
  }

  @JvmInline
  value class Word<T>(val word: T) : NGramToken<T>

  object EndOfSentence : NGramToken<Nothing> {
    override fun toString(): String = "</S>"
  }
}

/**
 * Abstract class representing an N-gram model.
 *
 * @param nGramCounter The N-gram counter for the model.
 * @param ngramSize N-gram size
 * @param T The type of tokens in the model.
 */
@ApiStatus.Internal
abstract class NGramModel<T>(protected val nGramCounter: NGramCounter<T>,
                             protected val ngramSize: Int) {
  abstract fun scoreToken(input: List<T>, tokenIndex: Int): Double

  fun getAllNGramsWithCount(): Map<NGram<out T>, Int> = nGramCounter.getAllNGramsWithCount()
}

/**
 * Converts a list of values into an N-gram.
 *
 * Note that the n-gram size is not considered, there is only one start of sentence,
 * and, if [withEndOfSentenceToken] is true, one end of sentence token added.
 *
 * The implementations are required to accept n-grams that can be smaller than the expected n-gram size,
 * as there is no padding with multiple start of sentence/end of sentence tokens.
 *
 * @param withEndOfSentenceToken Determines whether to include an end-of-sentence token in the N-gram.
 * @return The N-gram generated from the list of values.
 */
@ApiStatus.Internal
internal fun <T> Collection<T>.toNGram(withEndOfSentenceToken: Boolean): NGram<out T> {
  val additionalTokensCount = if (withEndOfSentenceToken) 2 else 1 // 1 for start of sentence, +1 if end of sentence is to be included

  return buildList(this.size + additionalTokensCount) {
    add(NGramToken.StartOfSentence)
    addAll(this@toNGram.map { NGramToken.Word(it) })

    if (withEndOfSentenceToken) {
      add(NGramToken.EndOfSentence)
    }
  }
}
