// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.impl.GotoNextErrorHandler;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public final class LightErrorNavigationTest extends LightJavaCodeInsightFixtureTestCase {
  public void testNavigateIntoParentheses() {
    myFixture.configureByText("Foo.java", """
      <caret>class Foo {
        void test(int x) {
      
        }
      
        void use() {
            test<error descr="Expected 1 argument but found 0">()</error>;
        }
      }
      """);
    myFixture.testHighlighting(true, false, false);
    assertNextErrorPosition("""
      class Foo {
        void test(int x) {
      
        }
      
        void use() {
            test(<caret>);
        }
      }
      """);
  }

  private void assertNextErrorPosition(String expected) {
    new GotoNextErrorHandler(true).invoke(getProject(), myFixture.getEditor(), myFixture.getFile());
    int offset = myFixture.getCaretOffset();
    String text = myFixture.getEditor().getDocument().getText();
    String expectedPos = text.substring(0, offset) + "<caret>" + text.substring(offset);
    assertEquals(expected, expectedPos);
  }
}
