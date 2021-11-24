// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.actions;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.impl.AbstractEditorTest;

public class JavaEditorActionTest extends AbstractEditorTest {
  public void testDeleteToWordStartWithEscapeChars() {
    init("class Foo { String s = \"a\\nb<caret>\"; }", JavaFileType.INSTANCE);
    executeAction(IdeActions.ACTION_EDITOR_DELETE_TO_WORD_START);
    checkResultByText("class Foo { String s = \"a\\n<caret>\"; }");
  }

  public void testDeleteToWordEndWithEscapeChars() {
    init("class Foo { String s = \"a\\<caret>nb\"; }", JavaFileType.INSTANCE);
    executeAction(IdeActions.ACTION_EDITOR_DELETE_TO_WORD_END);
    checkResultByText("class Foo { String s = \"a\\<caret>b\"; }");
  }

  public void testToggleCaseForTextAfterEscapedSlash() {
    init("class C { String s = \"<selection>ab\\\\cd<caret></selection>\"; }", JavaFileType.INSTANCE);
    executeAction(IdeActions.ACTION_EDITOR_TOGGLE_CASE);
    checkResultByText("class C { String s = \"<selection>AB\\\\CD<caret></selection>\"; }");
  }

  public void testToggleCaseForEscapedChar() {
    init("class C { String s = \"<selection>ab\\ncd<caret></selection>\"; }", JavaFileType.INSTANCE);
    executeAction(IdeActions.ACTION_EDITOR_TOGGLE_CASE);
    checkResultByText("class C { String s = \"<selection>AB\\nCD<caret></selection>\"; }");
  }

  public void testDeleteToWordStartWithEscapedQuote() {
    init("class Foo { String s = \"\\\"a<caret>\"; }", JavaFileType.INSTANCE);
    executeAction(IdeActions.ACTION_EDITOR_DELETE_TO_WORD_START);
    checkResultByText("class Foo { String s = \"\\\"<caret>\"; }");
  }

}
