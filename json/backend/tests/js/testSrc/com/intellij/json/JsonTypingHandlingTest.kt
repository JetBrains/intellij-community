// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.json.editor.JsonEditorOptions
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Test

@Suppress("SameParameterValue")
@TestApplication
class JsonTypingHandlingTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)

  private fun doTestEnter(before: String, expected: String) {
    doTypingTest('\n', before, expected, "json")
  }

  private fun doTestLBrace(before: String, expected: String) {
    doTypingTest('{', before, expected, "json")
  }

  private fun doTestLBracket(before: String, expected: String) {
    doTypingTest('[', before, expected, "json")
  }

  private fun doTestQuote(before: String, expected: String) {
    doTypingTest('"', before, expected, "json")
  }

  private fun doTestJsonlQuote(before: String, expected: String) {
    doTypingTest('"', before, expected, "jsonl")
  }

  private fun doTestSingleQuote(before: String, expected: String) {
    doTypingTest('\'', before, expected, "json")
  }

  private fun doTestColon(before: String, expected: String) {
    doTypingTest(':', before, expected, "json")
  }

  private fun doTestComma(before: String, expected: String) {
    doTypingTest(',', before, expected, "json")
  }

  private fun doTypingTest(
    c: Char,
    before: String,
    expected: String,
    extension: String,
  ) {
    myFixture.configureByText("test.$extension", before)
    myFixture.type(c)
    myFixture.checkResult(expected)
  }

  private fun doTypingTest(
    s: String,
    before: String,
    expected: String,
    extension: String,
  ) {
    myFixture.configureByText("test.$extension", before)
    myFixture.type(s)
    myFixture.checkResult(expected)
  }

  // JsonEnterHandler
  @Test
  fun testEnterAfterProperty() {
    doTestEnter(
      """
      {"a": true<caret>
      }
      """.trimIndent(),
      """
      {"a": true,
        <caret>
      }
      """.trimIndent()
    )
  }

  @Test
  fun testEnterMidProperty() {
    doTestEnter(
      """
      {"a": tr<caret>ue}
      """.trimIndent(),
      """
      {"a": true,
      }
      """.trimIndent()
    )
  }

  @Test
  fun testEnterMidObjectNoFollowing() {
    doTestEnter(
      """{"a": {<caret>}}""",
      """
      {"a": {
        
      }}
      """.trimIndent()
    )
  }

  @Test
  fun testEnterMidObjectWithFollowing() {
    doTestEnter(
      """
      {"a": {<caret>} "b": 5}
      """.trimIndent(),
      """
      {"a": {
        
      }, "b": 5}
      """.trimIndent()
    )
  }

  @Test
  fun testEnterAfterObject() {
    doTestEnter(
      """
      {"a": {}<caret>
      }
      """.trimIndent(),
      """
      {"a": {},
        <caret>
      }
      """.trimIndent()
    )
  }

  // JsonTypedHandler
  @Test
  fun testAutoCommaAfterLBraceInArray() {
    doTestLBrace(
      """ [ <caret> {"a": 5} ]""",
      """ [ {}, {"a": 5} ]""")
  }

  @Test
  fun testAutoCommaAfterLBracketInArray() {
    doTestLBracket(
      """ [ <caret> {"a": 5} ]""",
      """ [ [], {"a": 5} ]"""
    )
  }

  @Test
  fun testAutoCommaAfterQuoteInArray() {
    doTestQuote(
      """ [ <caret> {"a": 5} ]""",
      """ [ "", {"a": 5} ]"""
    )
  }

  @Test
  fun testAutoCommaAfterLBraceInObject() {
    doTestLBrace(
      """{ "x": <caret> "y": {"a": 5} }""",
      """{ "x": {}, "y": {"a": 5} }"""
    )
  }

  @Test
  fun testAutoCommaAfterLBracketInObject() {
    doTestLBracket(
      """{ "x": <caret> "y": {"a": 5} }""",
      """{ "x": [], "y": {"a": 5} }"""
    )
  }

  @Test
  fun testAutoCommaAfterQuoteInObject() {
    doTestQuote(
      """{ "x": <caret> "y": {"a": 5} }""",
      """{ "x": "", "y": {"a": 5} }"""
    )
  }

  @Test
  fun testNoAutoQuoteAfterEscapedQuote() {
    doTestQuote(
      """
        {
          "\"<caret>
        }
        """.trimIndent(),
      """
        {
          "\""
        }
        """.trimIndent(),
    )
  }

  @Test
  fun testNoAutoSingleQuoteAfterEscapedSingleQuote() {
    doTestSingleQuote(
      """
        {
          '\'<caret>
        }
        """.trimIndent(),
      """
        {
          '\''
        }
        """.trimIndent(),
    )
  }


  @Test
  fun testAutoQuotesForPropName() {
    doTestColon(
      "{ x<caret>}",
      """
      {
        "x": <caret>
      }
      """.trimIndent())
  }

  @Test
  fun testAutoQuotesForPropNameMulticaret() {
    doTestColon(
      """
      [
        {
          "a": 1,
          x<caret>
        },
        {
          "b": 2,
          y<caret>
        }
      ]
      """.trimIndent(),
      """
      [
        {
          "a": 1,
          "x": <caret>
        },
        {
          "b": 2,
          "y": <caret>
        }
      ]
      """.trimIndent()
    )
  }

  @Test
  fun testAutoQuotesForPropNameFalse1() {
    doTestColon(
      """{ "x"<caret>}""",
      """{ "x": <caret>}"""
    )
  }

  @Test
  fun testAutoQuotesForPropNameFalse2() {
    doTestColon(
      """{ "x<caret>"}""",
      """{ "x:<caret>"}"""
    )
  }

  @Test
  fun testAutoQuotesAndWhitespaceFollowingNewline() {
    doTestColon("""
      {
       "a": 5,
       x<caret>
       "q": 8
      }
    """.trimIndent(),
      """
      {
       "a": 5,
        "x": <caret>
       "q": 8
      }
    """.trimIndent())
  }

  @Test
  fun testAutoWhitespaceErasure() {
    myFixture.configureByText("test.json", "{a<caret>}")
    myFixture.type(":")
    myFixture.type(" ")
    myFixture.checkResult("""
      {
        "a": <caret>
      }
    """.trimIndent())
  }

  @Test
  fun testPairedSingleQuote() {
    doTypingTest('\'', "{<caret>}", "{'<caret>'}", "json")
  }

  @Test
  fun testPairedSingleQuote2() {
    doTypingTest('\'', """
      {
        "rules": {
          "at-rule-no-vendor-prefix": null,
          <caret>
        }
      }
    """.trimIndent(), """
      {
        "rules": {
          "at-rule-no-vendor-prefix": null,
          '<caret>'
        }
      }
    """.trimIndent(), "json")
  }

  @Test
  fun testNoCommaInNextQuotes() {
    testWithPairQuotes(false) {
      doTypingTest(""""ccc": """",
                   """{<caret>"aaa": "bbb"}""",
                   """{"ccc": "<caret>"aaa": "bbb"}""",
                   "json")
    }
  }

  @Test
  fun testNoCommaAfterArray() {
    testWithPairQuotes(false) {
      doTypingTest('"', """
        [
          {"aaa": [<caret>]},
          {}
        ]
      """.trimIndent(), """
        [
          {"aaa": ["<caret>]},
          {}
        ]
      """.trimIndent(), "json")
    }
  }

  @Test
  fun testAddCommaWithPairedQuotes() {
    testWithPairQuotes(true) {
      doTypingTest(""""ccc": """",
                   """{<caret>"aaa": "bbb"}""",
                   """{"ccc": "<caret>","aaa": "bbb"}""",
                   "json")
    }
  }

  @Test
  fun testNoCommaIfRBraceAndNoNewline() {
    doTestEnter("""
      {
        "x": 5<caret>}
      
    """.trimIndent(), """
      {
        "x": 5
      }
      
    """.trimIndent())
  }

  @Test
  fun testMoveColon() {
    val editorOptions = JsonEditorOptions.getInstance()
    val oldQuote = editorOptions.COLON_MOVE_OUTSIDE_QUOTES
    try {
      editorOptions.COLON_MOVE_OUTSIDE_QUOTES = true
      doTestColon(
        """{"x<caret>"}""",
        """{"x": <caret>}"""
      )
    }
    finally {
      editorOptions.COLON_MOVE_OUTSIDE_QUOTES = oldQuote
    }
  }

  @Test
  fun testMoveComma() {
    val editorOptions = JsonEditorOptions.getInstance()
    val oldQuote = editorOptions.COMMA_MOVE_OUTSIDE_QUOTES
    try {
      editorOptions.COMMA_MOVE_OUTSIDE_QUOTES = true
      doTestComma(
        """{"x": "value<caret>"}""",
        """{"x": "value",<caret>}"""
      )
    }
    finally {
      editorOptions.COMMA_MOVE_OUTSIDE_QUOTES = oldQuote
    }
  }

  @Test
  fun testMoveCommaForArray() {
    val editorOptions = JsonEditorOptions.getInstance()
    val oldQuote = editorOptions.COMMA_MOVE_OUTSIDE_QUOTES
    try {
      editorOptions.COMMA_MOVE_OUTSIDE_QUOTES = true
      doTestComma(
        """{"x": ["value<caret>"]}""",
        """{"x": ["value",<caret>]}"""
      )
    }
    finally {
      editorOptions.COMMA_MOVE_OUTSIDE_QUOTES = oldQuote
    }
  }

  @Test
  fun testDoNotMoveColonIfColon() {
    val editorOptions = JsonEditorOptions.getInstance()
    val oldQuote = editorOptions.COLON_MOVE_OUTSIDE_QUOTES
    try {
      editorOptions.COLON_MOVE_OUTSIDE_QUOTES = true
      doTestColon(
        """{"x<caret>":}""",
        """{"x:<caret>":}"""
      )
    }
    finally {
      editorOptions.COLON_MOVE_OUTSIDE_QUOTES = oldQuote
    }
  }

  @Test
  fun testDoNotMoveColonIfDisabled() {
    val editorOptions = JsonEditorOptions.getInstance()
    val oldQuote = editorOptions.COLON_MOVE_OUTSIDE_QUOTES
    try {
      editorOptions.COLON_MOVE_OUTSIDE_QUOTES = false
      doTestColon(
        """{"x<caret>"}""",
        """{"x:<caret>"}"""
      )
    }
    finally {
      editorOptions.COLON_MOVE_OUTSIDE_QUOTES = oldQuote
    }
  }

  @Test
  fun testDoNotMoveCommaIfComma() {
    val editorOptions = JsonEditorOptions.getInstance()
    val oldQuote = editorOptions.COMMA_MOVE_OUTSIDE_QUOTES
    try {
      editorOptions.COMMA_MOVE_OUTSIDE_QUOTES = true
      doTestComma(
        """{"x": "value<caret>",}""",
        """{"x": "value,<caret>",}"""
      )
    }
    finally {
      editorOptions.COMMA_MOVE_OUTSIDE_QUOTES = oldQuote
    }
  }

  @Test
  fun testDoNotMoveCommaIfDisabled() {
    val editorOptions = JsonEditorOptions.getInstance()
    val oldQuote = editorOptions.COMMA_MOVE_OUTSIDE_QUOTES
    try {
      editorOptions.COMMA_MOVE_OUTSIDE_QUOTES = false
      doTestComma(
        """{"x": "value<caret>"}""",
        """{"x": "value,<caret>"}"""
      )
    }
    finally {
      editorOptions.COMMA_MOVE_OUTSIDE_QUOTES = oldQuote
    }
  }

  @Test
  fun testDoNotMoveCommaForArrayIfComma() {
    val editorOptions = JsonEditorOptions.getInstance()
    val oldQuote = editorOptions.COMMA_MOVE_OUTSIDE_QUOTES
    try {
      editorOptions.COMMA_MOVE_OUTSIDE_QUOTES = true
      doTestComma(
        """{"x": ["value<caret>",]}""",
        """{"x": ["value,<caret>",]}"""
      )
    }
    finally {
      editorOptions.COMMA_MOVE_OUTSIDE_QUOTES = oldQuote
    }
  }

  @Test
  fun testQuotePairingNotInsideString01() {
    doTestQuote("""{"MyKey": "This \<caret>"}""",
                """{"MyKey": "This \"<caret>"}""")
  }

  @Test
  fun testQuotePairingNotInsideString02() {
    doTestSingleQuote(
      """{"MyKey": "This <caret> \"is\" my value"}""",
      """{"MyKey": "This ' \"is\" my value"}"""
    )
  }

  private fun testWithPairQuotes(on: Boolean, test: () -> Unit) {
    val settings = CodeInsightSettings.getInstance()
    val oldQuote = settings.AUTOINSERT_PAIR_QUOTE
    try {
      settings.AUTOINSERT_PAIR_QUOTE = on
      test()
    }
    finally {
      settings.AUTOINSERT_PAIR_QUOTE = oldQuote
    }
  }

  @Test
  fun testAutoCloseQuoteInJsonLines() {
    doTestJsonlQuote(
      """{"column1": ["c1.r2.i1", <caret>], "column2": "c2.r2"}""",
      """{"column1": ["c1.r2.i1", "<caret>"], "column2": "c2.r2"}"""
    )
  }

  @Test
  fun testAutoCloseQuoteSimpleJsonLines() {
    doTestJsonlQuote(
      """{<caret>}""",
      """{"<caret>"}"""
    )
  }

  @Test
  fun testAutoCommaAfterQuoteInJsonLinesArray() {
    doTestJsonlQuote(
      """ [ <caret> {"a": 5} ]""",
      """ [ "", {"a": 5} ]"""
    )
  }
}
