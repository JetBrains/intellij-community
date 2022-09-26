// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class AddToPermitsListTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK;
  }

  public void testNoPermitsList() {
    PsiClass aClass = myFixture.addClass("sealed class A {}");
    myFixture.configureByText("B.java", "final class B extends <caret>A {}");
    checkPreviewAndInvoke(aClass, "Add 'B' to permits list of a sealed class 'A'", "sealed class A permits B {}");
  }

  public void testNoPermitsListSameFileInheritors() {
    PsiClass aClass = myFixture.addClass("sealed class A {} record A1() extends A{} record A2() extends A{}");
    myFixture.configureByText("B.java", "final class B extends <caret>A {}");
    checkPreviewAndInvoke(aClass, "Add 'B' to permits list of a sealed class 'A'", "sealed class A permits A1, A2, B {}");
  }

  public void testUnorderedPermitsList() {
    PsiClass aClass = myFixture.addClass("sealed class A permits C {}");
    myFixture.addClass("non-sealed class C extends A {}");
    myFixture.configureByText("B.java", "final class B extends A<caret> {}");
    checkPreviewAndInvoke(aClass, "Add 'B' to permits list of a sealed class 'A'", "sealed class A permits B, C {}");
  }

  public void testMultipleInheritors() {
    PsiClass aClass = myFixture.addClass("sealed class A {}");
    myFixture.configureByText("C.java", "non-sealed class C extends <caret>A {}");
    checkPreviewAndInvoke(aClass, "Add 'C' to permits list of a sealed class 'A'", "sealed class A permits C {}");
    myFixture.configureByText("B.java", "final class B extends <caret>A {}");
    checkPreviewAndInvoke(aClass, "Add 'B' to permits list of a sealed class 'A'", "sealed class A permits B, C {}");
  }

  public void testMultipleParents() {
    PsiClass aClass = myFixture.addClass("sealed interface A {}");
    myFixture.addClass("interface B {}");
    myFixture.configureByText("C.java", "final class C implements A<caret>, B {}");
    checkPreviewAndInvoke(aClass, "Add 'C' to permits list of a sealed class 'A'", "sealed interface A permits C {}");
  }

  private void checkPreviewAndInvoke(PsiClass aClass, String hint, String expectedText) {
    IntentionAction intention = myFixture.findSingleIntention(hint);
    String previewText = myFixture.getIntentionPreviewText(intention);
    assertEquals(expectedText, previewText);
    myFixture.launchAction(intention);
    assertEquals(expectedText, aClass.getText());
  }
}
