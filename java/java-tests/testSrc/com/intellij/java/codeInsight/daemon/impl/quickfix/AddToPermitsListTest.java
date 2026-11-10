// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class AddToPermitsListTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_19);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testNoPermitsList() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass aClass = myFixture.addClass("sealed class A {}");
    myFixture.configureByText("B.java", "final class B extends <caret>A {}");
    checkPreviewAndInvoke(aClass, "Add 'B' to permits list of sealed class 'A'", "sealed class A permits B {}");
  }

  @Test
  public void testNoPermitsListSameFileInheritors() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass aClass = myFixture.addClass("sealed class A {} record A1() extends A{} record A2() extends A{}");
    myFixture.configureByText("B.java", "final class B extends <caret>A {}");
    checkPreviewAndInvoke(aClass, "Add 'B' to permits list of sealed class 'A'", 
                          "sealed class A permits A1, A2, B {} record A1() extends A{} record A2() extends A{}",
                          "sealed class A permits A1, A2, B {}");
  }

  @Test
  public void testUnorderedPermitsList() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass aClass = myFixture.addClass("sealed class A permits C {}");
    myFixture.addClass("non-sealed class C extends A {}");
    myFixture.configureByText("B.java", "final class B extends A<caret> {}");
    checkPreviewAndInvoke(aClass, "Add 'B' to permits list of sealed class 'A'", "sealed class A permits B, C {}");
  }

  @Test
  public void testMultipleInheritors() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass aClass = myFixture.addClass("sealed class A {}");
    myFixture.configureByText("C.java", "non-sealed class C extends <caret>A {}");
    checkPreviewAndInvoke(aClass, "Add 'C' to permits list of sealed class 'A'", "sealed class A permits C {}");
    myFixture.configureByText("B.java", "final class B extends <caret>A {}");
    checkPreviewAndInvoke(aClass, "Add 'B' to permits list of sealed class 'A'", "sealed class A permits B, C {}");
  }

  @Test
  public void testMultipleParents() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass aClass = myFixture.addClass("sealed interface A {}");
    myFixture.addClass("interface B {}");
    myFixture.configureByText("C.java", "final class C implements A<caret>, B {}");
    checkPreviewAndInvoke(aClass, "Add 'C' to permits list of sealed class 'A'", "sealed interface A permits C {}");
  }

  private void checkPreviewAndInvoke(PsiClass aClass, String hint, String expectedText) {
    checkPreviewAndInvoke(aClass, hint, expectedText, expectedText);
  }

  private void checkPreviewAndInvoke(PsiClass aClass, String hint, String expectedPreview, String expectedClass) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IntentionAction intention = myFixture.findSingleIntention(hint);
    String previewText = myFixture.getIntentionPreviewText(intention);
    assertEquals(expectedPreview, previewText);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.launchAction(intention);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      assertEquals(expectedClass, aClass.getText());
    });
  }
}
