// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.editorActions;

import com.intellij.openapi.editor.impl.AbstractEditorTest;
import com.intellij.testFramework.TestFileType;

public class JavaEditingTest extends AbstractEditorTest {
  public void testSmartHomeInJavadoc() {
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

  public void testSmartHomeWithSelectionInJavadoc() {
    init("/**\n" +
         " * some text<caret>\n" +
         " */\n" +
         "class C {}",
         TestFileType.JAVA);
    homeWithSelection();
    checkResultByText("/**\n" +
                      " * <selection><caret>some text</selection>\n" +
                      " */\n" +
                      "class C {}");
  }
}
