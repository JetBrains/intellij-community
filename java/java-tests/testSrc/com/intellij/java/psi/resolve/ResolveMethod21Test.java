// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.resolve;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.LightResolveFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.LightResolveFixtureKt.lightResolveFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class ResolveMethod21Test {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<LightResolveFixture> resolveFixture = lightResolveFixture(projectFixture);

  @Test
  public void testResolveInsideLambdaSwitchSelector() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    PsiFile file = myFixture.configureByFile("method/" + resolveFixture.get().getTestName(false) + ".java");
    ReadAction.runBlocking(() -> {
      PsiReference reference = file.findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
      PsiElement target = reference.resolve();
      assertInstanceOf(PsiMethod.class, target);
      assertEquals("collect", ((PsiMethod)target).getName());

      int index = myFixture.getEditor().getDocument().getText().indexOf("diag.getCode");
      PsiReference getCodeReference = file.findReferenceAt(index + 6);
      PsiElement getCodeTarget = getCodeReference.resolve();
      assertInstanceOf(PsiMethod.class, getCodeTarget);
      assertEquals("getCode", ((PsiMethod)getCodeTarget).getName());
    });
  }

  @Test
  public void testVarWithDiamondInsideLambdaDuringOverloadResolution() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    PsiFile file = myFixture.configureByFile("method/" + resolveFixture.get().getTestName(false) + ".java");
    ReadAction.runBlocking(() -> {
      PsiReference reference = file.findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
      assertNotNull(reference);
      PsiElement target = reference.resolve();
      assertInstanceOf(PsiMethod.class, target);
      assertEquals("java.util.function.Supplier<? extends T>",
                   ((PsiMethod)target).getParameterList().getParameters()[1].getType().getCanonicalText());
    });
  }
}
