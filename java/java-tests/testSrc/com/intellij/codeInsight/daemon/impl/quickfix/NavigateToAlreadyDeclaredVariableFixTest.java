// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class NavigateToAlreadyDeclaredVariableFixTest extends LightJavaCodeInsightFixtureTestCase {
  public void testNavigateToVariableOfDifferentType() {
    myFixture.configureByText("A.java", "class A {{int i = 0; i++; long <caret>i = 0;}}");
    IntentionAction intention = myFixture.findSingleIntention(QuickFixBundle.message("navigate.variable.declaration.text", "i"));
    assertNotNull(intention);
    myFixture.launchAction(intention);
    assertEquals(14, myFixture.getCaretOffset());
  }

  public void testNavigateFromParameter() {
    myFixture.configureByText("A.java", """
      class A {void f(String[] elements){
              String element = "hello";
      for (String el<caret>ement : elements){}}}""");
    IntentionAction intention = myFixture.findSingleIntention(QuickFixBundle.message("navigate.variable.declaration.text", "element"));
    assertNotNull(intention);
    myFixture.launchAction(intention);
    assertEquals(51, myFixture.getCaretOffset());
  }
}
