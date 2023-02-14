// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath

import com.intellij.json.psi.JsonFile
import com.intellij.jsonpath.ui.IncorrectDocument
import com.intellij.jsonpath.ui.IncorrectExpression
import com.intellij.jsonpath.ui.JsonPathEvaluator
import com.intellij.jsonpath.ui.ResultString
import com.intellij.testFramework.assertions.Assertions.assertThat
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixture4TestCase
import com.jayway.jsonpath.Configuration
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.Option
import com.jayway.jsonpath.spi.json.JacksonJsonProvider
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.*

@RunWith(JUnit4::class)
class JsonPathEvaluateTest : LightPlatformCodeInsightFixture4TestCase() {
  companion object {
    init {
      Configuration.setDefaults(object : Configuration.Defaults {
        private val jsonProvider = JacksonJsonProvider()
        private val mappingProvider = JacksonMappingProvider()

        override fun jsonProvider() = jsonProvider

        override fun mappingProvider() = mappingProvider

        override fun options() = EnumSet.noneOf(Option::class.java)
      })
    }
  }
  @Test
  fun evaluateWithBindingFromClasspath() {
    val documentContext = JsonPath.parse("{ \"some\": 12 }")
    val data = documentContext.read<Any>("$.some")

    assertEquals(12, data)
  }

  @Test
  fun evaluateWithOptionsAndDocument() {
    val document = myFixture.configureByText("data.json", sampleDocument)
    val evaluator = JsonPathEvaluator(document as JsonFile, "$..book[?(@.price > 0.1)]", setOf(Option.AS_PATH_LIST))
    val result = evaluator.evaluate() as ResultString

    assertEquals("[\"\$['store']['book'][0]\", \"\$['store']['book'][1]\"]", result.value)
  }

  @Test
  fun primitiveResult() {
    val document = myFixture.configureByText("data.json", sampleDocument)
    val evaluator = JsonPathEvaluator(document as JsonFile, "$.store.book[0].price", setOf())
    val result = evaluator.evaluate() as ResultString

    assertEquals("0.15", result.value)
  }

  @Test
  fun collectionResult() {
    val document = myFixture.configureByText("data.json", sampleDocument)
    val evaluator = JsonPathEvaluator(document as JsonFile, "$.store.book.*.price", setOf())
    val result = evaluator.evaluate() as ResultString

    assertEquals("[0.15, 7.99]", result.value)
  }

  @Test
  fun nullResult() {
    val document = myFixture.configureByText("data.json", sampleDocument)
    val evaluator = JsonPathEvaluator(document as JsonFile, "$.country", setOf())
    val result = evaluator.evaluate() as ResultString

    assertEquals("null", result.value)
  }

  @Test
  fun stringResult() {
    val document = myFixture.configureByText("data.json", sampleDocument)
    val evaluator = JsonPathEvaluator(document as JsonFile, "$.store.book[0].category", setOf())
    val result = evaluator.evaluate() as ResultString

    assertEquals("\"reference\"", result.value)
  }

  @Test
  fun treeResult() {
    val document = myFixture.configureByText("data.json", sampleDocument)
    val evaluator = JsonPathEvaluator(document as JsonFile, "$.store.book[1]", setOf())
    val result = evaluator.evaluate() as ResultString

    assertEquals("{\"category\":\"fiction\",\"author\":\"Peter Pan\",\"title\":\"No Future\",\"price\":7.99}", result.value)
  }

  @Test
  fun incorrectExpression() {
    val document = myFixture.configureByText("data.json", sampleDocument)
    val evaluator = JsonPathEvaluator(document as JsonFile, "$$", setOf())
    val result = evaluator.evaluate() as IncorrectExpression

    assertEquals("Illegal character at position 1 expected '.' or '['", result.message)
  }

  @Test
  fun incorrectDocument() {
    val document = myFixture.configureByText("my.json", "{ invalid }")
    val evaluator = JsonPathEvaluator(document as JsonFile, "$", setOf())
    val result = evaluator.evaluate() as IncorrectDocument
    assertThat(result.message).contains("Unexpected character ('i' (code 105)): was expecting double-quote to start field name")
  }

  private val sampleDocument: String
    get() = /* language=JSON */ """
      {
        "store": {
          "book": [
            {
              "category": "reference",
              "author": "Sam Smith",
              "title": "First way",
              "price": 0.15
            },
            {
              "category": "fiction",
              "author": "Peter Pan",
              "title": "No Future",
              "price": 7.99
            }
          ],
          "bicycle": {
              "color": "white",
              "price": 2.95
          }
        },
        "country": null,
        "expensive": 10
      }                       
    """.trimIndent()
}