// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.ngram.model.counter

import com.intellij.platform.ml.impl.ngram.model.NGram
import org.jetbrains.annotations.ApiStatus

/**
 * An interface representing a counter for n-grams.
 *
 * @param T the type of elements in the n-grams
 */
@ApiStatus.Internal
interface NGramCounter<T> {
  /**
   * Returns the frequency of the specified n-gram.
   *
   * @param ngram the n-gram to get the frequency of
   * @return the frequency of the specified n-gram
   */
  fun getFrequencyOf(ngram: NGram<out T>): Int

  /**
   * Retrieves all the N-grams with their respective counts.
   *
   * @return A map containing the N-grams as keys and their corresponding counts as values.
   */
  fun getAllNGramsWithCount(): Map<NGram<out T>, Int>
}
