// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class DuplicateActionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testOneLine() {
    doTest("""
             xxx<caret>
             """, "txt", """
             xxx
             xxx<caret>
             """);
  }

  @Test
  public void testEmpty() {
    doTest("<caret>", "txt", """

      <caret>""");
  }

  private void doTest(String before, @NonNls String ext, String after) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a." + ext, before);
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_DUPLICATE);
    myFixture.checkResult(after);
  }

  @Test
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

  @Test
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

  @Test
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
