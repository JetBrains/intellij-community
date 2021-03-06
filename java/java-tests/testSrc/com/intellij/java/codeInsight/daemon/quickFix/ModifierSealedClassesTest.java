// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class ModifierSealedClassesTest extends LightJavaCodeInsightFixtureTestCase {

  public void testNoInheritors() {
    PsiClass childClass = myFixture.addClass("package foo; class Child extends Parent {}");
    myFixture.configureByText("Parent.java", "package foo; sealed class Parent permits <caret>Child {}");
    myFixture.findSingleIntention("Make 'Child' non-sealed");
    IntentionAction makeFinal = myFixture.findSingleIntention("Make 'Child' final");
    myFixture.launchAction(makeFinal);
    assertEquals("final class Child extends Parent {}", childClass.getText());
  }
  
  public void testSubclassWithInheritors() {
    PsiClass childClass = myFixture.addClass("package foo; class Child extends Parent {}");
    myFixture.addClass("package foo; class GrandChild extends Child {}");
    myFixture.configureByText("Parent.java", "package foo; sealed class Parent permits <caret>Child {}");
    assertEmpty(myFixture.filterAvailableIntentions("Make 'Child' final"));
    IntentionAction makeNonSealed = myFixture.findSingleIntention("Make 'Child' non-sealed");
    myFixture.launchAction(makeNonSealed);
    assertEquals("non-sealed class Child extends Parent {}", childClass.getText());
  }
  
  public void testDoNotSuggestFinalModifierForSubclassWithInheritors() {
    myFixture.addClass("package foo; sealed class Parent permits Child {}");
    myFixture.addClass("package foo; class GrandChild extends Child {}");
    myFixture.configureByText("Child.java", "package foo; class <caret>Child extends Parent {}");
    assertEmpty(myFixture.filterAvailableIntentions("Make 'Child' final"));
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return LightJavaCodeInsightFixtureTestCase.JAVA_15;
  }
}
