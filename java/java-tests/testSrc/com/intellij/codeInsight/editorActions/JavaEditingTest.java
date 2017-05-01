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
package com.intellij.codeInsight.editorActions;

import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.testFramework.TestFileType;

public class JavaEditingTest extends AbstractEditorTest {
  public void testSmartHomeInJavadoc() throws Exception {
    init("/**\n" +
         " * some text<caret>\n" +
         " */\n" +
         "class C {}", 
         TestFileType.JAVA);
    home();
    checkResultByText("/**\n" +
                      " * <caret>some text\n" +
                      " */\n" +
                      "class C {}");
  }
}
