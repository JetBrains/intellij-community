// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.impl.GotoNextErrorHandler;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public final class LightErrorNavigationTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testNavigateIntoParentheses() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
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
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      new GotoNextErrorHandler(true).invoke(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile());
      int offset = myFixture.getCaretOffset();
      String text = myFixture.getEditor().getDocument().getText();
      String expectedPos = text.substring(0, offset) + "<caret>" + text.substring(offset);
      assertEquals(expected, expectedPos);
    });
  }
}
