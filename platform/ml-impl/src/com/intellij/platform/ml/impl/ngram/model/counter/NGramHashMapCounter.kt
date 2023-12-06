// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.ngram.model.counter

import com.intellij.platform.ml.impl.ngram.model.NGram

/**
 * Implementation of the [NGramCounter] that uses a [HashMap] for storing n-grams.
 *
 * @param T the type of elements in the n-grams.
 * @property mapToFrequency a map representing the frequency of n-grams.
 * @property ngramSize the size of the n-grams.
 */
internal class NGramHashMapCounter<T>(private val mapToFrequency: Map<NGram<out T>, Int>,
                                      private val ngramSize: Int) : NGramCounter<T> {

  /**
   * Returns the frequency of the given ngram in the NGram collection.
   *
   * If the given [ngram] is of the same size as [ngramSize], the precise count of this n-gram will be returned,
   * if the size is greater, then only the last [ngramSize] tokens will be taken, then the same logic follows as for a precise size n-gram,
   * should the size be smaller, then the function will return the count of all n-grams starting with the same tokens (context count)
   */

  override fun getFrequencyOf(ngram: NGram<out T>): Int {
    return when {
      ngram.size == ngramSize -> mapToFrequency[ngram] ?: 0
      ngram.size > ngramSize -> mapToFrequency[ngram.takeLast(ngramSize)] ?: 0
      else -> mapToFrequency.filterKeys { it.subList(0, ngram.size) == ngram }.values.sum()
    }
  }

  override fun getAllNGramsWithCount(): Map<NGram<out T>, Int> = mapToFrequency
}