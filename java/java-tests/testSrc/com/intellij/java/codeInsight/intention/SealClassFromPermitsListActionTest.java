// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.intention;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.SealClassFromPermitsListAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.psi.PsiClass;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class SealClassFromPermitsListActionTest extends LightJavaCodeInsightFixtureTestCase {
  
  public void testSimple() {
    PsiClass childClass = myFixture.addClass("package foo; class Child extends Parent {}");
    PsiClass grandChildClass = myFixture.addClass("package foo; class GrandChild extends Child {}");
    myFixture.configureByText("Parent.java", "package foo; sealed class Parent permits <caret>Child {}");
    doTest(true);
    assertEquals("sealed class Child extends Parent permits GrandChild {}", childClass.getText());
    assertEquals("non-sealed class GrandChild extends Child {}", grandChildClass.getText());
  }
  
  public void testNoInheritors() {
    myFixture.addClass("package foo; class Child extends Parent {}");
    myFixture.configureByText("Parent.java", "package foo; sealed class Parent permits <caret>Child {}");
    doTest(false);
  }

  public void testSubInterface() {
    myFixture.addClass("package foo; interface Child extends Parent {}");
    myFixture.configureByText("Parent.java", "package foo; sealed interface Parent permits <caret>Child {}");
    assertTrue(myFixture.filterAvailableIntentions("Make 'Child' final").isEmpty());
  }

  private void doTest(boolean isAvailable) {
    IntentionAction action = new SealClassFromPermitsListAction(
      PsiTreeUtil.getNonStrictParentOfType(myFixture.getElementAtCaret(), PsiClass.class)).asIntention();
    if (isAvailable) {
      assertTrue(action.isAvailable(getProject(), getEditor(), getFile()));
      myFixture.launchAction(action);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    }
    else {
      assertFalse(action.isAvailable(getProject(), getEditor(), getFile()));
    }
  }
}
