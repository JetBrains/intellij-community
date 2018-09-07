// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;

public class JsonCopyPasteTest extends CodeInsightFixtureTestCase {

  private void doCopyPasteTest(String source, String dest, String expected, String filename1, String filename2) {
    myFixture.configureByText(filename1, source);
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_COPY);
    myFixture.configureByText(filename2, dest);
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE);
    myFixture.checkResult(expected);
  }

  private void doTestFromTextToJson(String source, String dest, String expected) {
    doCopyPasteTest(source, dest, expected, "dummy.txt", "dummy.json");
  }

  private void doTestFromJsonToText(String source, String dest, String expected) {
    doCopyPasteTest(source, dest, expected, "dummy.json", "dummy.txt");
  }

  public void testUnescapeQuotes() {
    doTestFromJsonToText("{\"p\": \"<selection>\\\"quoted\\\"</selection>\"}", "<caret>", "\"quoted\"");
  }

  public void testUnescapeWhitespaces() {
    doTestFromJsonToText("{\"p\": \"<selection>lorem ipsum\\tdolor sit amet</selection>\"}", "<caret>", "lorem ipsum\tdolor sit amet");
  }

  public void testUnescapeFromPropNames() {
    doTestFromJsonToText("{\"<selection>lorem ipsum\\tdolor sit amet</selection>\": \"foo\"}", "<caret>", "lorem ipsum\tdolor sit amet");
  }

  public void testEscapeQuotes() {
    doTestFromTextToJson("<selection>\"quoted\"</selection>", "{\"p\": \"<caret>\"}", "{\"p\": \"\\\"quoted\\\"\"}");
  }

  public void testEscapeWhitespaces() {
    doTestFromTextToJson("<selection>lorem ipsum\tdolor sit amet</selection>", "{\"p\": \"<caret>\"}", "{\"p\": \"lorem ipsum\\tdolor sit amet\"}");
  }

  public void testEscapeInPropNames() {
    doTestFromTextToJson("<selection>lorem ipsum\tdolor sit amet</selection>", "{\"<caret>\": \"foo\"}", "{\"lorem ipsum\\tdolor sit amet\": \"foo\"}");
  }
}
