// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.editorActions;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.editor.impl.AbstractEditorTest;

public class JavaEditingTest extends AbstractEditorTest {
  public void testSmartHomeInJavadoc() {
    init("/**\n" +
         " * some text<caret>\n" +
         " */\n" +
         "class C {}",
         JavaFileType.INSTANCE);
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
         JavaFileType.INSTANCE);
    homeWithSelection();
    checkResultByText("/**\n" +
                      " * <selection><caret>some text</selection>\n" +
                      " */\n" +
                      "class C {}");
  }
}
