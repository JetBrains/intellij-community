// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json;

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

  @SuppressWarnings("SameParameterValue")
  private void doTypingTest(char c,
                            @NotNull String before,
                            @NotNull String expected,
                            @NotNull String extension) {
    myFixture.configureByText("test." + extension, before);
    myFixture.type(c);
    myFixture.checkResult(expected);
  }

  // JsonEnterHandler
  public void testEnterAfterProperty() {
    doTestEnter("{\"a\": true<caret>}", "{\"a\": true,\n}");
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
    doTestEnter("{\"a\": {}<caret>}", "{\"a\": {},\n}");
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
}
