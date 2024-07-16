// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NonNls;

public class DuplicateActionTest extends LightJavaCodeInsightFixtureTestCase {
  public void testOneLine() {
    doTest("""
             xxx<caret>
             """, "txt", """
             xxx
             xxx<caret>
             """);
  }

  public void testEmpty() {
    doTest("<caret>", "txt", """

      <caret>""");
  }

  private void doTest(String before, @NonNls String ext, String after) {
    myFixture.configureByText("a." + ext, before);
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DUPLICATE);
    myFixture.checkResult(after);
  }

  public void testSelectName() {
    doTest("""
             class C {
               void foo() {}<caret>
             }
             """, "java", """
             class C {
               void foo() {}
               void <caret>foo() {}
             }
             """);
  }

  public void testPreserveCaretPositionWhenItIsAlreadyInsideElementName() {
    doTest("""
             class C {
               void fo<caret>o() {}
             }
             """, "java", """
             class C {
               void foo() {}
               void fo<caret>o() {}
             }
             """);
  }

  public void testXmlTag() {
    doTest("""
             <root>
               <foo/><caret>
             </root>
             """, "xml", """
             <root>
               <foo/>
               <foo/><caret>
             </root>
             """);
  }
}
