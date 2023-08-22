// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.ngram.model

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

private const val ASSERTION_DELTA = 1.0 / 1000.0

internal class SimpleNGramModelTest {
  private val corpus = listOf(
    "I am Sam",
    "Sam I am",
    "I do not like green eggs and ham"
  ).map { it.split(" " )}

  /**
   * Basic test - check if first word is counted correctly (n-gram start token is included)
   */
  @Test
  fun `test bigram first word in sentence`() {
    val bigramModel = SimpleNGramModel.train(corpus, 2)

    val input = listOf("I")
    Assertions.assertEquals(2.0 / 3.0, bigramModel.scoreToken(input, 0), ASSERTION_DELTA)
  }

  @Test
  fun `test bigram second word (usual case)`() {
    val bigramModel = SimpleNGramModel.train(corpus, 2)

    val input = "I am".split(" ")
    Assertions.assertEquals(2.0 / 3.0, bigramModel.scoreToken(input, 1), ASSERTION_DELTA)
  }


  @Test
  fun `test n-grams get truncated with longer input`() {
    val bigramModel = SimpleNGramModel.train(corpus, 2)

    val input = "I am Sam".split(" ")
    Assertions.assertEquals(1.0 / 2.0, bigramModel.scoreToken(input, 2), ASSERTION_DELTA)
  }

  /**
   * The reason behind this test, is that only one star tof sentence token is added,
   * so it's worth making sure that everything works correctly for the first word.
   */
  @Test
  fun `test 3-gram first word score is correct (2-gram is used for the score)`() {
    val model = SimpleNGramModel.train(corpus, 3)

    val input = listOf("I")
    Assertions.assertEquals(2.0 / 3.0, model.scoreToken(input, 0), ASSERTION_DELTA)
  }

  @Test
  fun `test 6-gram model on shorter inputs`() {
    val model = SimpleNGramModel.train(corpus, 6)

    val input = "I am".split(" ")
    Assertions.assertEquals(1.0 / 2.0, model.scoreToken(input, 1), ASSERTION_DELTA)
  }

  @Test
  fun `test get all bigrams`() {
    val model = SimpleNGramModel.train(corpus, 2)

    val expected = mapOf(
      listOf(NGramToken.StartOfSentence, NGramToken.Word("I")) to 2,
      listOf(NGramToken.Word("I"), NGramToken.Word("am")) to 2,
      listOf(NGramToken.Word("am"), NGramToken.Word("Sam")) to 1,
      listOf(NGramToken.Word("Sam"), NGramToken.EndOfSentence) to 1,
      listOf(NGramToken.StartOfSentence, NGramToken.Word("Sam")) to 1,
      listOf(NGramToken.Word("Sam"), NGramToken.Word("I")) to 1,
      listOf(NGramToken.Word("am"), NGramToken.EndOfSentence) to 1,
      listOf(NGramToken.Word("I"), NGramToken.Word("do")) to 1,
      listOf(NGramToken.Word("do"), NGramToken.Word("not")) to 1,
      listOf(NGramToken.Word("not"), NGramToken.Word("like")) to 1,
      listOf(NGramToken.Word("like"), NGramToken.Word("green")) to 1,
      listOf(NGramToken.Word("green"), NGramToken.Word("eggs")) to 1,
      listOf(NGramToken.Word("eggs"), NGramToken.Word("and")) to 1,
      listOf(NGramToken.Word("and"), NGramToken.Word("ham")) to 1,
      listOf(NGramToken.Word("ham"), NGramToken.EndOfSentence) to 1,
    )
    val actual = model.getAllNGramsWithCount()

    Assertions.assertEquals(expected, actual)
  }
}