// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.impl;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.PsiClassUtil;
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
public final class PsiClassUtilTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testIsThrowable() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByText("Test.java", """
      class Test extends RuntimeException {}
      class Test2 extends java.util.ArrayList<String> {}
      // Return true only for well-formed inheritors
      class Test3 implements RuntimeException {}
      class Test4 extends String, RuntimeException {}
      class Test5<T> extends RuntimeException {}
      """);
    ReadAction.runBlocking(() -> {
      PsiClass[] classes = ((PsiJavaFile)file).getClasses();
      assertTrue(PsiClassUtil.isThrowable(classes[0]));
      assertFalse(PsiClassUtil.isThrowable(classes[1]));
      assertFalse(PsiClassUtil.isThrowable(classes[2]));
      assertFalse(PsiClassUtil.isThrowable(classes[3]));
      assertFalse(PsiClassUtil.isThrowable(classes[4]));
    });
  }

  @Test
  public void testIsThrowableNoRecursion() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.configureByText("Test.java",
                                             """
                                               class Test extends Test {}
                                               class Test1 extends Test2 {}
                                               class Test2 extends Test1 {}
                                               """);
    ReadAction.runBlocking(() -> {
      PsiClass[] classes = ((PsiJavaFile)file).getClasses();
      assertFalse(PsiClassUtil.isThrowable(classes[0]));
      assertFalse(PsiClassUtil.isThrowable(classes[1]));
      assertFalse(PsiClassUtil.isThrowable(classes[2]));
    });
  }
}
