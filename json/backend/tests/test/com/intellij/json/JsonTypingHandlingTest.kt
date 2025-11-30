// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.json.editor.JsonEditorOptions

@Suppress("SameParameterValue")
class JsonTypingHandlingTest : JsonTestCase() {
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
    extension: String
  ) {
    myFixture.configureByText("test.$extension", before)
    myFixture.type(c)
    myFixture.checkResult(expected)
  }

  private fun doTypingTest(
    s: String,
    before: String,
    expected: String,
    extension: String
  ) {
    myFixture.configureByText("test.$extension", before)
    myFixture.type(s)
    myFixture.checkResult(expected)
  }

  // JsonEnterHandler
  fun testEnterAfterProperty() {
    doTestEnter("{\"a\": true<caret>\n}", "{\"a\": true,\n  <caret>\n}")
  }

  fun testEnterMidProperty() {
    doTestEnter("{\"a\": tr<caret>ue}", "{\"a\": true,\n}")
  }

  fun testEnterMidObjectNoFollowing() {
    doTestEnter("{\"a\": {<caret>}}", "{\"a\": {\n  \n}}")
  }

  fun testEnterMidObjectWithFollowing() {
    doTestEnter("{\"a\": {<caret>} \"b\": 5}", "{\"a\": {\n  \n}, \"b\": 5}")
  }

  fun testEnterAfterObject() {
    doTestEnter("{\"a\": {}<caret>\n}", "{\"a\": {},\n  <caret>\n}")
  }

  // JsonTypedHandler
  fun testAutoCommaAfterLBraceInArray() {
    doTestLBrace("[ <caret> {\"a\": 5} ]", "[ {}, {\"a\": 5} ]")
  }

  fun testAutoCommaAfterLBracketInArray() {
    doTestLBracket("[ <caret> {\"a\": 5} ]", "[ [], {\"a\": 5} ]")
  }

  fun testAutoCommaAfterQuoteInArray() {
    doTestQuote("[ <caret> {\"a\": 5} ]", "[ \"\", {\"a\": 5} ]")
  }

  fun testAutoCommaAfterLBraceInObject() {
    doTestLBrace("{ \"x\": <caret> \"y\": {\"a\": 5} }", "{ \"x\": {}, \"y\": {\"a\": 5} }")
  }

  fun testAutoCommaAfterLBracketInObject() {
    doTestLBracket("{ \"x\": <caret> \"y\": {\"a\": 5} }", "{ \"x\": [], \"y\": {\"a\": 5} }")
  }

  fun testAutoCommaAfterQuoteInObject() {
    doTestQuote("{ \"x\": <caret> \"y\": {\"a\": 5} }", "{ \"x\": \"\", \"y\": {\"a\": 5} }")
  }

  fun testAutoQuotesForPropName() {
    doTestColon("{ x<caret>}", """
      {
        "x": <caret>
      }
      """.trimIndent())
  }

  fun testAutoQuotesForPropNameFalse1() {
    doTestColon("{ \"x\"<caret>}", "{ \"x\": <caret>}")
  }

  fun testAutoQuotesForPropNameFalse2() {
    doTestColon("{ \"x<caret>\"}", "{ \"x:<caret>\"}")
  }

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

  fun testPairedSingleQuote() {
    doTypingTest('\'', "{<caret>}", "{'<caret>'}", "json")
  }

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

  fun testNoCommaInNextQuotes() {
    testWithPairQuotes(false,
                       Runnable {
                         doTypingTest("\"ccc\": \"", "{<caret>\"aaa\": \"bbb\"}", "{\"ccc\": \"<caret>\"aaa\": \"bbb\"}", "json")
                       })
  }

  fun testNoCommaAfterArray() {
    testWithPairQuotes(false, Runnable {
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
    })
  }

  fun testAddCommaWithPairedQuotes() {
    testWithPairQuotes(true, Runnable {
      doTypingTest("\"ccc\": \"", "{<caret>\"aaa\": \"bbb\"}", "{\"ccc\": \"<caret>\",\"aaa\": \"bbb\"}", "json")
    })
  }

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

  fun testMoveColon() {
    val editorOptions = JsonEditorOptions.getInstance()
    val oldQuote = editorOptions.COLON_MOVE_OUTSIDE_QUOTES
    try {
      editorOptions.COLON_MOVE_OUTSIDE_QUOTES = true
      doTestColon("{\"x<caret>\"}", "{\"x\": <caret>}")
    }
    finally {
      editorOptions.COLON_MOVE_OUTSIDE_QUOTES = oldQuote
    }
  }

  fun testMoveComma() {
    val editorOptions = JsonEditorOptions.getInstance()
    val oldQuote = editorOptions.COMMA_MOVE_OUTSIDE_QUOTES
    try {
      editorOptions.COMMA_MOVE_OUTSIDE_QUOTES = true
      doTestComma("{\"x\": \"value<caret>\"}", "{\"x\": \"value\",<caret>}")
    }
    finally {
      editorOptions.COMMA_MOVE_OUTSIDE_QUOTES = oldQuote
    }
  }

  fun testMoveCommaForArray() {
    val editorOptions = JsonEditorOptions.getInstance()
    val oldQuote = editorOptions.COMMA_MOVE_OUTSIDE_QUOTES
    try {
      editorOptions.COMMA_MOVE_OUTSIDE_QUOTES = true
      doTestComma("{\"x\": [\"value<caret>\"]}", "{\"x\": [\"value\",<caret>]}")
    }
    finally {
      editorOptions.COMMA_MOVE_OUTSIDE_QUOTES = oldQuote
    }
  }

  fun testDoNotMoveColonIfColon() {
    val editorOptions = JsonEditorOptions.getInstance()
    val oldQuote = editorOptions.COLON_MOVE_OUTSIDE_QUOTES
    try {
      editorOptions.COLON_MOVE_OUTSIDE_QUOTES = true
      doTestColon("{\"x<caret>\":}", "{\"x:<caret>\":}")
    }
    finally {
      editorOptions.COLON_MOVE_OUTSIDE_QUOTES = oldQuote
    }
  }

  fun testDoNotMoveColonIfDisabled() {
    val editorOptions = JsonEditorOptions.getInstance()
    val oldQuote = editorOptions.COLON_MOVE_OUTSIDE_QUOTES
    try {
      editorOptions.COLON_MOVE_OUTSIDE_QUOTES = false
      doTestColon("{\"x<caret>\"}", "{\"x:<caret>\"}")
    }
    finally {
      editorOptions.COLON_MOVE_OUTSIDE_QUOTES = oldQuote
    }
  }

  fun testDoNotMoveCommaIfComma() {
    val editorOptions = JsonEditorOptions.getInstance()
    val oldQuote = editorOptions.COMMA_MOVE_OUTSIDE_QUOTES
    try {
      editorOptions.COMMA_MOVE_OUTSIDE_QUOTES = true
      doTestComma("{\"x\": \"value<caret>\",}", "{\"x\": \"value,<caret>\",}")
    }
    finally {
      editorOptions.COMMA_MOVE_OUTSIDE_QUOTES = oldQuote
    }
  }

  fun testDoNotMoveCommaIfDisabled() {
    val editorOptions = JsonEditorOptions.getInstance()
    val oldQuote = editorOptions.COMMA_MOVE_OUTSIDE_QUOTES
    try {
      editorOptions.COMMA_MOVE_OUTSIDE_QUOTES = false
      doTestComma("{\"x\": \"value<caret>\"}", "{\"x\": \"value,<caret>\"}")
    }
    finally {
      editorOptions.COMMA_MOVE_OUTSIDE_QUOTES = oldQuote
    }
  }

  fun testDoNotMoveCommaForArrayIfComma() {
    val editorOptions = JsonEditorOptions.getInstance()
    val oldQuote = editorOptions.COMMA_MOVE_OUTSIDE_QUOTES
    try {
      editorOptions.COMMA_MOVE_OUTSIDE_QUOTES = true
      doTestComma("{\"x\": [\"value<caret>\",]}", "{\"x\": [\"value,<caret>\",]}")
    }
    finally {
      editorOptions.COMMA_MOVE_OUTSIDE_QUOTES = oldQuote
    }
  }

  fun testQuotePairingNotInsideString01() {
    doTestQuote("{\"MyKey\": \"This \\<caret>\"}", "{\"MyKey\": \"This \\\"<caret>\"}")
  }

  fun testQuotePairingNotInsideString02() {
    doTestSingleQuote("{\"MyKey\": \"This <caret> \\\"is\\\" my value\"}", "{\"MyKey\": \"This ' \\\"is\\\" my value\"}")
  }

  companion object {
    private fun testWithPairQuotes(on: Boolean, test: Runnable) {
      val settings = CodeInsightSettings.getInstance()
      val oldQuote = settings.AUTOINSERT_PAIR_QUOTE
      try {
        settings.AUTOINSERT_PAIR_QUOTE = on
        test.run()
      }
      finally {
        settings.AUTOINSERT_PAIR_QUOTE = oldQuote
      }
    }
  }
}
