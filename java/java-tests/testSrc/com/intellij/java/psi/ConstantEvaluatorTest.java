// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Dmitry Avdeev
 */
@TestApplication
public class ConstantEvaluatorTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testPrefixExpressionEvaluation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiJavaFile file = (PsiJavaFile)myFixture.configureByText("A.java", "class A {public static final  int VALUE =  ~0 >>> 1;}");
    ReadAction.runBlocking(() -> {
      PsiClass aClass = file.getClasses()[0];
      PsiField vField = aClass.getFields()[0];
      Object result = JavaPsiFacade.getInstance(myFixture.getProject()).getConstantEvaluationHelper().computeConstantExpression(vField.getInitializer());

      assertEquals(2147483647, result);
    });
  }
}
