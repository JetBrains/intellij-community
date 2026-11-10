// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class ModifierSealedClassesTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testNoInheritors() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass childClass = myFixture.addClass("package foo; class Child extends Parent {}");
    myFixture.configureByText("Parent.java", "package foo; sealed class Parent permits <caret>Child {}");
    myFixture.findSingleIntention("Make 'Child' non-sealed");
    IntentionAction makeFinal = myFixture.findSingleIntention("Make 'Child' final");
    myFixture.launchAction(makeFinal);
    ReadAction.runBlocking(() -> assertEquals("final class Child extends Parent {}", childClass.getText()));
  }
  
  @Test
  public void testSubclassWithInheritors() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass childClass = myFixture.addClass("package foo; class Child extends Parent {}");
    myFixture.addClass("package foo; class GrandChild extends Child {}");
    myFixture.configureByText("Parent.java", "package foo; sealed class Parent permits <caret>Child {}");
    assertThat(myFixture.filterAvailableIntentions("Make 'Child' final")).isNullOrEmpty();
    IntentionAction makeNonSealed = myFixture.findSingleIntention("Make 'Child' non-sealed");
    myFixture.launchAction(makeNonSealed);
    ReadAction.runBlocking(() -> assertEquals("non-sealed class Child extends Parent {}", childClass.getText()));
  }
  
  @Test
  public void testDoNotSuggestFinalModifierForSubclassWithInheritors() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package foo; sealed class Parent permits Child {}");
    myFixture.addClass("package foo; class GrandChild extends Child {}");
    myFixture.configureByText("Child.java", "package foo; class <caret>Child extends Parent {}");
    assertThat(myFixture.filterAvailableIntentions("Make 'Child' final")).isNullOrEmpty();
  }
}
