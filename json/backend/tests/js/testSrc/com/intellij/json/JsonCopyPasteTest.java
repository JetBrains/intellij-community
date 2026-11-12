// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.json;

import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@TestApplication
public class JsonCopyPasteTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private void doCopyPasteTest(String source, String dest, String expected, String filename1, String filename2) {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
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

  @Test
  public void testUnescapeQuotes() {
    doTestFromJsonToText("{\"p\": \"<selection>\\\"quoted\\\"</selection>\"}", "<caret>", "\"quoted\"");
  }

  @Test
  public void testUnescapeWhitespaces() {
    doTestFromJsonToText("{\"p\": \"<selection>lorem ipsum\\tdolor sit amet</selection>\"}", "<caret>", "lorem ipsum\tdolor sit amet");
  }

  @Test
  public void testUnescapeFromPropNames() {
    doTestFromJsonToText("{\"<selection>lorem ipsum\\tdolor sit amet</selection>\": \"foo\"}", "<caret>", "lorem ipsum\tdolor sit amet");
  }

  @Test
  public void testEscapeQuotes() {
    doTestFromTextToJson("<selection>\"quoted\"</selection>", "{\"p\": \"<caret>\"}", "{\"p\": \"\\\"quoted\\\"\"}");
  }

  @Test
  public void testEscapeWhitespaces() {
    doTestFromTextToJson("<selection>lorem ipsum\tdolor sit amet</selection>", "{\"p\": \"<caret>\"}", "{\"p\": \"lorem ipsum\\tdolor sit amet\"}");
  }

  @Test
  public void testEscapeInPropNames() {
    doTestFromTextToJson("<selection>lorem ipsum\tdolor sit amet</selection>", "{\"<caret>\": \"foo\"}", "{\"lorem ipsum\\tdolor sit amet\": \"foo\"}");
  }

  @Test
  public void testAddTrailingComma() {
    doTestFromTextToJson("<selection>\"x\": 5</selection>", "{\"q\": \"foo\"<caret>}", "{\"q\": \"foo\",\"x\": 5}");
  }

  @Test
  public void testAddRemoveTrailingComma() {
    doTestFromTextToJson("<selection>\"x\": 5,</selection>", "{\"q\": \"foo\"<caret>}", "{\"q\": \"foo\",\"x\": 5}");
  }

  @Test
  public void testAddLeadingComma() {
    doTestFromTextToJson("<selection>\"x\": 5</selection>", "{<caret>\"q\": \"foo\"}", "{\"x\": 5,\"q\": \"foo\"}");
  }

  @Test
  public void testDoNothingLeadingComma() {
    doTestFromTextToJson("<selection>\"x\": 5,</selection>", "{<caret>\"q\": \"foo\"}", "{\"x\": 5,\"q\": \"foo\"}");
  }

  @Test
  public void testCommasMidPropList() {
    doTestFromTextToJson("<selection>\"x\": 5</selection>", "{\"s\": \"foo\",<caret>\"q\": \"foo\"}", "{\"s\": \"foo\",\"x\": 5,\"q\": \"foo\"}");
  }

  @Test
  public void testAddTrailingCommaArray() {
    doTestFromTextToJson("<selection>\"a\"</selection>", "[4<caret>]", "[4,\"a\"]");
  }

  @Test
  public void testAddRemoveTrailingCommaArray() {
    doTestFromTextToJson("<selection>\"a\",</selection>", "[4<caret>]", "[4,\"a\"]");
  }

  @Test
  public void testAddLeadingCommaArray() {
    doTestFromTextToJson("<selection>\"a\"</selection>", "[<caret>4]", "[\"a\",4]");
  }

  @Test
  public void testCommasMidPropListArray() {
    doTestFromTextToJson("<selection>\"a\"</selection>", "[3,<caret>4]", "[3,\"a\",4]");
  }

  @Test
  public void testWithLeadingAndTrailingWhitespaces() {
    doTestFromTextToJson("<selection>  \t     \"a\": true   \t     </selection>", "{\"q\": 5<caret>}", "{\"q\": 5,  \t     \"a\": true   \t     }");
  }

  @Test
  public void testWithLeadingAndTrailingWhitespacesArray() {
    doTestFromTextToJson("<selection>  \t     \"a\"   \t     </selection>", "[\"q\"<caret>]", "[\"q\",  \t     \"a\"   \t     ]");
  }

  @Test
  public void testWithLeadingAndTrailingWhitespacesBefore() {
    doTestFromTextToJson("<selection>  \t     \"a\": true   \t     </selection>", "{<caret>\"q\": 5}", "{  \t     \"a\": true,   \t     \"q\": 5}");
  }

  @Test
  public void testWithLeadingAndTrailingWhitespacesArrayBefore() {
    doTestFromTextToJson("<selection>  \t     \"a\"   \t     </selection>", "[<caret>\"q\"]", "[  \t     \"a\",   \t     \"q\"]");
  }

  @Test
  public void testTrailingNewline() {
    doTestFromTextToJson("    \"react-dom\": \"^16.5.2\"\n", """
      {
        "name": "untitled",
        "version": "1.0.0",
        "dependencies": {
          "react"<caret>: "^16.5.2",
          "react-dom": "^16.5.2"
        }
      }""", """
                           {
                             "name": "untitled",
                             "version": "1.0.0",
                             "dependencies": {
                               "react-dom": "^16.5.2",
                               "react": "^16.5.2",
                               "react-dom": "^16.5.2"
                             }
                           }""");
  }
}
