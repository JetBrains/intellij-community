// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.intention;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.SealClassFromPermitsListAction;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class SealClassFromPermitsListActionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testSimple() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass childClass = myFixture.addClass("package foo; class Child extends Parent {}");
    PsiClass grandChildClass = myFixture.addClass("package foo; class GrandChild extends Child {}");
    myFixture.configureByText("Parent.java", "package foo; sealed class Parent permits <caret>Child {}");
    doTest(true);
    ReadAction.runBlocking(() -> {
      assertEquals("sealed class Child extends Parent permits GrandChild {}", childClass.getText());
      assertEquals("non-sealed class GrandChild extends Child {}", grandChildClass.getText());
    });
  }
  
  @Test
  public void testNoInheritors() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package foo; class Child extends Parent {}");
    myFixture.configureByText("Parent.java", "package foo; sealed class Parent permits <caret>Child {}");
    doTest(false);
  }

  @Test
  public void testSubInterface() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package foo; interface Child extends Parent {}");
    myFixture.configureByText("Parent.java", "package foo; sealed interface Parent permits <caret>Child {}");
    assertTrue(myFixture.filterAvailableIntentions("Make 'Child' final").isEmpty());
  }

  private void doTest(boolean isAvailable) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      IntentionAction action = new SealClassFromPermitsListAction(
        PsiTreeUtil.getNonStrictParentOfType(myFixture.getElementAtCaret(), PsiClass.class)).asIntention();
      if (isAvailable) {
        assertTrue(action.isAvailable(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile()));
        myFixture.launchAction(action);
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      }
      else {
        assertFalse(action.isAvailable(myFixture.getProject(), myFixture.getEditor(), myFixture.getFile()));
      }
    });
  }
}
