// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.impl.source.tree.java;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.impl.source.tree.java.PsiReferenceExpressionImpl;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class SeemsScrambledTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testIdAnnotation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass aClass = myFixture.addClass("public @interface Id {}");
    assertFalse(ReadAction.computeBlocking(() -> PsiReferenceExpressionImpl.seemsScrambledByStructure(aClass)));
  }

  @Test
  public void testInnerEnum() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass aClass = myFixture.addClass("public class Foo { enum v1 {} }");
    assertFalse(ReadAction.computeBlocking(() -> PsiReferenceExpressionImpl.seemsScrambledByStructure(aClass.getInnerClasses()[0])));
  }

  @Test
  public void testScrambled() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass aClass = myFixture.addClass("public class a { void b() {} }");
    assertTrue(ReadAction.computeBlocking(() -> PsiReferenceExpressionImpl.seemsScrambledByStructure(aClass)));
  }

  @Test
  public void testHasNonScrambledMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass aClass = myFixture.addClass("public class a { void doSomething() {} }");
    assertFalse(ReadAction.computeBlocking(() -> PsiReferenceExpressionImpl.seemsScrambledByStructure(aClass)));
  }
}
