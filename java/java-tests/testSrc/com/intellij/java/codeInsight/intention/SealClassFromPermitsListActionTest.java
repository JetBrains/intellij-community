// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.intention;

import com.intellij.codeInsight.intention.impl.SealClassFromPermitsListAction;
import com.intellij.psi.PsiClass;
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

  private void doTest(boolean isAvailable) {
    SealClassFromPermitsListAction action = new SealClassFromPermitsListAction(myFixture.getElementAtCaret());
    if (isAvailable) {
      assertTrue(action.isAvailable(getProject(), getEditor(), getFile()));
      myFixture.launchAction(action);
    }
    else {
      assertFalse(action.isAvailable(getProject(), getEditor(), getFile()));
    }
  }
}
