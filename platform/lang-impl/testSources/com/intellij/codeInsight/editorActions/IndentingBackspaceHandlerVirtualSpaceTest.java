/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.LogicalPosition;
import com.intellij.testFramework.LightPlatformCodeInsightTestCase;

import java.io.IOException;

public class IndentingBackspaceHandlerVirtualSpaceTest extends LightPlatformCodeInsightTestCase {
  public void testAfterLargeIndent() throws IOException {
    doTest("class Foo {\n" +
           "      \n" +
           "}",
           new LogicalPosition(1, 10),
           "class Foo {\n" +
           "    \n" +
           "}",
           new LogicalPosition(1, 4));
  }

  public void testAfterProperIndent() throws IOException {
    doTest("class Foo {\n" +
           "    \n" +
           "}",
           new LogicalPosition(1, 10),
           "class Foo {\n" +
           "    \n" +
           "}",
           new LogicalPosition(1, 4));
  }

  public void testAfterSmallIndent() throws IOException {
    doTest("class Foo {\n" +
           "   \n" +
           "}",
           new LogicalPosition(1, 10),
           "class Foo {\n" +
           "    \n" +
           "}",
           new LogicalPosition(1, 4));
  }

  public void testAfterEmptyIndent() throws IOException {
    doTest("class Foo {\n" +
           "\n" +
           "}",
           new LogicalPosition(1, 10),
           "class Foo {\n" +
           "    \n" +
           "}",
           new LogicalPosition(1, 4));
  }

  public void testAtIndent() throws IOException {
    doTest("class Foo {\n" +
           "   \n" +
           "}",
           new LogicalPosition(1, 4),
           "class Foo {\n" +
           "}",
           new LogicalPosition(0, 11));
  }

  public void testAtIndentOnEmptyLine() throws IOException {
    doTest("class Foo {\n" +
           "\n" +
           "}",
           new LogicalPosition(1, 4),
           "class Foo {\n" +
           "}",
           new LogicalPosition(0, 11));
  }

  public void testBeforeIndent() throws IOException {
    doTest("class Foo {\n" +
           "  \n" +
           "}",
           new LogicalPosition(1, 3),
           "class Foo {\n" +
           "}",
           new LogicalPosition(0, 11));
  }

  public void testDeleteLine() throws IOException {
    doTest("class Foo {\n" +
           "\n" +
           "\n" +
           "}",
           new LogicalPosition(2, 0),
           "class Foo {\n" +
           "    \n" +
           "}",
           new LogicalPosition(1, 4));
  }

  private void doTest(String textBefore, LogicalPosition caretBefore, String textAfter, LogicalPosition caretAfter) throws IOException {
    configureFromFileText(getTestName(false) + ".java", textBefore);
    myEditor.getSettings().setVirtualSpace(true);
    myEditor.getCaretModel().moveToLogicalPosition(caretBefore);
    backspace();
    checkResultByText(textAfter);
    assertEquals(caretAfter, myEditor.getCaretModel().getLogicalPosition());
  }
}
