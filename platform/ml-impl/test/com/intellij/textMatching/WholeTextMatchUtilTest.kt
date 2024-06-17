// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.textMatching

import junit.framework.TestCase.assertEquals
import org.junit.Test

class WholeTextMatchUtilTest {
  private fun assertMapsEquals(expected: Map<*, *>, actual: Map<*, *>) {
    assertEquals(expected.size, actual.size)
    assertEquals(expected.keys, actual.keys)
    for (key in expected.keys) {
      assertEquals("Value not equal for key $key.", expected[key], actual[key])
    }
  }

  @Test
  fun `test one word simple`() {
    val actual = WholeTextMatchUtil.calculateFeatures("foo", "foo")
    assertMapsEquals(mapOf<String, Any>(
      "levenshtein_distance" to 0.0,
      "levenshtein_distance_case_insensitive" to 0.0,
      "words_in_query" to 1,
      "words_in_element" to 1,
      "exactly_matched_words" to 1
    ), actual)
  }

  @Test
  fun `test one word Capitalized`() {
    val actual = WholeTextMatchUtil.calculateFeatures("foo", "Foo")
    assertMapsEquals(mapOf<String, Any>(
      "levenshtein_distance" to 1.0 / 3,
      "levenshtein_distance_case_insensitive" to 0.0,
      "words_in_query" to 1,
      "words_in_element" to 1,
      "exactly_matched_words" to 1
    ), actual)
  }

  @Test
  fun `test other word Capitalized`() {
    val actual = WholeTextMatchUtil.calculateFeatures("Foo", "foo")
    assertMapsEquals(mapOf<String, Any>(
      "levenshtein_distance" to 1.0 / 3,
      "levenshtein_distance_case_insensitive" to 0.0,
      "words_in_query" to 1,
      "words_in_element" to 1,
      "exactly_matched_words" to 1
    ), actual)
  }

  @Test
  fun `test query ALL CAPS`() {
    val actual = WholeTextMatchUtil.calculateFeatures("Foo", "FOO")
    assertMapsEquals(mapOf<String, Any>(
      "levenshtein_distance" to 2.0 / 3,
      "levenshtein_distance_case_insensitive" to 0.0,
      "words_in_query" to 1,
      "words_in_element" to 1,
      "exactly_matched_words" to 1
    ), actual)
  }

  @Test
  fun `test multi word query`() {
    val actual = WholeTextMatchUtil.calculateFeatures("fooBar", "FooBar")
    assertMapsEquals(mapOf<String, Any>(
      "levenshtein_distance" to 1.0 / 6,
      "levenshtein_distance_case_insensitive" to 0.0,
      "words_in_query" to 2,
      "words_in_element" to 2,
      "exactly_matched_words" to 2
    ), actual)
  }

  @Test
  fun `test partial query`() {
    val actual = WholeTextMatchUtil.calculateFeatures("FooBar", "foo")
    assertMapsEquals(mapOf<String, Any>(
      "levenshtein_distance" to 4.0 / 3,
      "levenshtein_distance_case_insensitive" to 1.0,
      "words_in_query" to 1,
      "words_in_element" to 2,
      "exactly_matched_words" to 1
    ), actual)
  }

  @Test
  fun `test snake_case to camelCase`() {
    val actual = WholeTextMatchUtil.calculateFeatures("foo_bar", "fooBar")
    assertMapsEquals(mapOf<String, Any>(
      "levenshtein_distance" to 2.0 / 6,
      "levenshtein_distance_case_insensitive" to 1.0 / 6,
      "words_in_query" to 2,
      "words_in_element" to 2,
      "exactly_matched_words" to 2
    ), actual)
  }

  @Test
  fun `test camelCase to snake_case`() {
    val actual = WholeTextMatchUtil.calculateFeatures("fooBar", "foo_bar")
    assertMapsEquals(mapOf<String, Any>(
      "levenshtein_distance" to 2.0 / 7,
      "levenshtein_distance_case_insensitive" to 1.0 / 7,
      "words_in_query" to 2,
      "words_in_element" to 2,
      "exactly_matched_words" to 2
    ), actual)
  }
  @Test
  fun `test space-split words`() {
    val actual = WholeTextMatchUtil.calculateFeatures("fooBar", "foo bar")
    assertMapsEquals(mapOf<String, Any>(
      "levenshtein_distance" to 2.0 / 7,
      "levenshtein_distance_case_insensitive" to 1.0 / 7,
      "words_in_query" to 2,
      "words_in_element" to 2,
      "exactly_matched_words" to 2
    ), actual)
  }

  @Test
  fun `test word order matters`() {
    val actual = WholeTextMatchUtil.calculateFeatures("barFoo", "fooBar")
    assertMapsEquals(mapOf<String, Any>(
      "levenshtein_distance" to 1.0,
      "levenshtein_distance_case_insensitive" to 1.0,
      "words_in_query" to 2,
      "words_in_element" to 2,
      "exactly_matched_words" to 0
    ), actual)
  }

  @Test
  fun `test not starting with first word `() {
    val actual = WholeTextMatchUtil.calculateFeatures("barFoo", "fooBar")
    assertMapsEquals(mapOf<String, Any>(
      "levenshtein_distance" to 1.0,
      "levenshtein_distance_case_insensitive" to 1.0,
      "words_in_query" to 2,
      "words_in_element" to 2,
      "exactly_matched_words" to 0
    ), actual)
  }
}