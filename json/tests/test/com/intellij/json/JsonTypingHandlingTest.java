// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.json.editor.JsonEditorOptions;
import org.jetbrains.annotations.NotNull;

public class JsonTypingHandlingTest extends JsonTestCase {
  private void doTestEnter(@NotNull final String before, @NotNull final String expected) {
    doTypingTest('\n', before, expected, "json");
  }
  private void doTestLBrace(@NotNull final String before, @NotNull final String expected) {
    doTypingTest('{', before, expected, "json");
  }
  private void doTestLBracket(@NotNull final String before, @NotNull final String expected) {
    doTypingTest('[', before, expected, "json");
  }
  private void doTestQuote(@NotNull final String before, @NotNull final String expected) {
    doTypingTest('"', before, expected, "json");
  }
  private void doTestColon(@NotNull final String before, @NotNull final String expected) {
    doTypingTest(':', before, expected, "json");
  }
  private void doTestComma(@NotNull final String before, @NotNull final String expected) {
    doTypingTest(',', before, expected, "json");
  }

  @SuppressWarnings("SameParameterValue")
  private void doTypingTest(char c,
                            @NotNull String before,
                            @NotNull String expected,
                            @NotNull String extension) {
    myFixture.configureByText("test." + extension, before);
    myFixture.type(c);
    myFixture.checkResult(expected);
  }

  @SuppressWarnings("SameParameterValue")
  private void doTypingTest(String s,
                            @NotNull String before,
                            @NotNull String expected,
                            @NotNull String extension) {
    myFixture.configureByText("test." + extension, before);
    myFixture.type(s);
    myFixture.checkResult(expected);
  }

  // JsonEnterHandler
  public void testEnterAfterProperty() {
    doTestEnter("{\"a\": true<caret>\n}", "{\"a\": true,\n  <caret>\n}");
  }
  public void testEnterMidProperty() {
    doTestEnter("{\"a\": tr<caret>ue}", "{\"a\": true,\n}");
  }
  public void testEnterMidObjectNoFollowing() {
    doTestEnter("{\"a\": {<caret>}}", "{\"a\": {\n  \n}}");
  }
  public void testEnterMidObjectWithFollowing() {
    doTestEnter("{\"a\": {<caret>} \"b\": 5}", "{\"a\": {\n  \n}, \"b\": 5}");
  }
  public void testEnterAfterObject() {
    doTestEnter("{\"a\": {}<caret>\n}", "{\"a\": {},\n  <caret>\n}");
  }

  // JsonTypedHandler
  public void testAutoCommaAfterLBraceInArray() {
    doTestLBrace("[ <caret> {\"a\": 5} ]", "[ {}, {\"a\": 5} ]");
  }
  public void testAutoCommaAfterLBracketInArray() {
    doTestLBracket("[ <caret> {\"a\": 5} ]", "[ [], {\"a\": 5} ]");
  }
  public void testAutoCommaAfterQuoteInArray() {
    doTestQuote("[ <caret> {\"a\": 5} ]", "[ \"\", {\"a\": 5} ]");
  }
  public void testAutoCommaAfterLBraceInObject() {
    doTestLBrace("{ \"x\": <caret> \"y\": {\"a\": 5} }", "{ \"x\": {}, \"y\": {\"a\": 5} }");
  }
  public void testAutoCommaAfterLBracketInObject() {
    doTestLBracket("{ \"x\": <caret> \"y\": {\"a\": 5} }", "{ \"x\": [], \"y\": {\"a\": 5} }");
  }
  public void testAutoCommaAfterQuoteInObject() {
    doTestQuote("{ \"x\": <caret> \"y\": {\"a\": 5} }", "{ \"x\": \"\", \"y\": {\"a\": 5} }");
  }
  public void testAutoQuotesForPropName() {
    doTestColon( "{ x<caret>}", "{\n" +
                                "  \"x\": <caret>\n" +
                                "}");
  }
  public void testAutoQuotesForPropNameFalse1() {
    doTestColon( "{ \"x\"<caret>}", "{ \"x\": <caret>}");
  }
  public void testAutoQuotesForPropNameFalse2() {
    doTestColon( "{ \"x<caret>\"}", "{ \"x:<caret>\"}");
  }
  public void testAutoQuotesAndWhitespaceFollowingNewline() {
    doTestColon("{\n" +
                " \"a\": 5,\n" +
                " x<caret>\n" +
                " \"q\": 8\n" +
                "}",
                "{\n" +
                " \"a\": 5,\n" +
                "  \"x\": <caret>\n" +
                " \"q\": 8\n" +
                "}");
  }

  public void testAutoWhitespaceErasure() {
    myFixture.configureByText("test.json", "{a<caret>}");
    myFixture.type(":");
    myFixture.type(" ");
    myFixture.checkResult("{\n" +
                          "  \"a\": <caret>\n" +
                          "}");
  }

  public void testPairedSingleQuote() {
    doTypingTest('\'', "{<caret>}", "{'<caret>'}", "json");
  }
  public void testPairedSingleQuote2() {
    doTypingTest('\'', "{\n" +
                       "  \"rules\": {\n" +
                       "    \"at-rule-no-vendor-prefix\": null,\n" +
                       "    <caret>\n" +
                       "  }\n" +
                       "}", "{\n" +
                            "  \"rules\": {\n" +
                            "    \"at-rule-no-vendor-prefix\": null,\n" +
                            "    '<caret>'\n" +
                            "  }\n" +
                            "}", "json");
  }

  private static void testWithPairQuotes(boolean on, Runnable test) {
    CodeInsightSettings settings = CodeInsightSettings.getInstance();
    boolean oldQuote = settings.AUTOINSERT_PAIR_QUOTE;
    try {
      settings.AUTOINSERT_PAIR_QUOTE = on;
      test.run();
    }
    finally {
      settings.AUTOINSERT_PAIR_QUOTE = oldQuote;
    }
  }

  public void testNoCommaInNextQuotes() {
    testWithPairQuotes(false,
                       () -> doTypingTest("\"ccc\": \"", "{<caret>\"aaa\": \"bbb\"}", "{\"ccc\": \"<caret>\"aaa\": \"bbb\"}", "json"));
  }

  public void testNoCommaAfterArray() {
    testWithPairQuotes(false, () ->
      doTypingTest('"', "[\n" +
                                    "  {\"aaa\": [<caret>]},\n" +
                                    "  {}\n" +
                                    "]", "[\n" +
                                         "  {\"aaa\": [\"<caret>]},\n" +
                                         "  {}\n" +
                                         "]", "json"));
  }

  public void testAddCommaWithPairedQuotes() {
    testWithPairQuotes(true, () -> doTypingTest("\"ccc\": \"", "{<caret>\"aaa\": \"bbb\"}", "{\"ccc\": \"<caret>\",\"aaa\": \"bbb\"}", "json"));
  }

  public void testNoCommaIfRBraceAndNoNewline() {
    doTestEnter("{\n" +
                "  \"x\": 5<caret>}\n", "{\n" +
                                 "  \"x\": 5\n" +
                                 "}\n");
  }

  public void testMoveColon() {
    JsonEditorOptions editorOptions = JsonEditorOptions.getInstance();
    boolean oldQuote = editorOptions.COLON_MOVE_OUTSIDE_QUOTES;
    try {
      editorOptions.COLON_MOVE_OUTSIDE_QUOTES = true;
      doTestColon("{\"x<caret>\"}", "{\"x\": <caret>}");
    }
    finally {
      editorOptions.COLON_MOVE_OUTSIDE_QUOTES = oldQuote;
    }
  }

  public void testMoveComma() {
    JsonEditorOptions editorOptions = JsonEditorOptions.getInstance();
    boolean oldQuote = editorOptions.COMMA_MOVE_OUTSIDE_QUOTES;
    try {
      editorOptions.COMMA_MOVE_OUTSIDE_QUOTES = true;
      doTestComma("{\"x\": \"value<caret>\"}", "{\"x\": \"value\",<caret>}");
    }
    finally {
      editorOptions.COMMA_MOVE_OUTSIDE_QUOTES = oldQuote;
    }
  }

  public void testMoveCommaForArray() {
    JsonEditorOptions editorOptions = JsonEditorOptions.getInstance();
    boolean oldQuote = editorOptions.COMMA_MOVE_OUTSIDE_QUOTES;
    try {
      editorOptions.COMMA_MOVE_OUTSIDE_QUOTES = true;
      doTestComma("{\"x\": [\"value<caret>\"]}", "{\"x\": [\"value\",<caret>]}");
    }
    finally {
      editorOptions.COMMA_MOVE_OUTSIDE_QUOTES = oldQuote;
    }
  }

  public void testDoNotMoveColonIfColon() {
    JsonEditorOptions editorOptions = JsonEditorOptions.getInstance();
    boolean oldQuote = editorOptions.COLON_MOVE_OUTSIDE_QUOTES;
    try {
      editorOptions.COLON_MOVE_OUTSIDE_QUOTES = true;
      doTestColon("{\"x<caret>\":}", "{\"x:<caret>\":}");
    }
    finally {
      editorOptions.COLON_MOVE_OUTSIDE_QUOTES = oldQuote;
    }
  }

  public void testDoNotMoveColonIfDisabled() {
    JsonEditorOptions editorOptions = JsonEditorOptions.getInstance();
    boolean oldQuote = editorOptions.COLON_MOVE_OUTSIDE_QUOTES;
    try {
      editorOptions.COLON_MOVE_OUTSIDE_QUOTES = false;
      doTestColon("{\"x<caret>\"}", "{\"x:<caret>\"}");
    }
    finally {
      editorOptions.COLON_MOVE_OUTSIDE_QUOTES = oldQuote;
    }
  }

  public void testDoNotMoveCommaIfComma() {
    JsonEditorOptions editorOptions = JsonEditorOptions.getInstance();
    boolean oldQuote = editorOptions.COMMA_MOVE_OUTSIDE_QUOTES;
    try {
      editorOptions.COMMA_MOVE_OUTSIDE_QUOTES = true;
      doTestComma("{\"x\": \"value<caret>\",}", "{\"x\": \"value,<caret>\",}");
    }
    finally {
      editorOptions.COMMA_MOVE_OUTSIDE_QUOTES = oldQuote;
    }
  }

  public void testDoNotMoveCommaIfDisabled() {
    JsonEditorOptions editorOptions = JsonEditorOptions.getInstance();
    boolean oldQuote = editorOptions.COMMA_MOVE_OUTSIDE_QUOTES;
    try {
      editorOptions.COMMA_MOVE_OUTSIDE_QUOTES = false;
      doTestComma("{\"x\": \"value<caret>\"}", "{\"x\": \"value,<caret>\"}");
    }
    finally {
      editorOptions.COMMA_MOVE_OUTSIDE_QUOTES = oldQuote;
    }
  }

  public void testDoNotMoveCommaForArrayIfComma() {
    JsonEditorOptions editorOptions = JsonEditorOptions.getInstance();
    boolean oldQuote = editorOptions.COMMA_MOVE_OUTSIDE_QUOTES;
    try {
      editorOptions.COMMA_MOVE_OUTSIDE_QUOTES = true;
      doTestComma("{\"x\": [\"value<caret>\",]}", "{\"x\": [\"value,<caret>\",]}");
    }
    finally {
      editorOptions.COMMA_MOVE_OUTSIDE_QUOTES = oldQuote;
    }
  }
}
