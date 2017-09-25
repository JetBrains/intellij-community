/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.psi.formatter.java;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.testFramework.TestFileType;

public class JavaAutoIndentLinesTest extends AbstractEditorTest {
  public void testSelection() {
    init("class C {\n" +
         "int <selection>a<caret></selection> = 1;\n" +
         "}",
         TestFileType.JAVA);
    executeAction(IdeActions.ACTION_EDITOR_AUTO_INDENT_LINES);
    checkResultByText("class C {\n" +
                      "    int <selection>a<caret></selection> = 1;\n" +
                      "}");
  }
}
