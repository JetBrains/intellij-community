// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaFile;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

/**
 * @author Dmitry Avdeev
 */
public class ConstantEvaluatorTest extends LightJavaCodeInsightFixtureTestCase {

  public void testPrefixExpressionEvaluation() {
    PsiJavaFile file = (PsiJavaFile)myFixture.configureByText("A.java", "class A {public static final  int VALUE =  ~0 >>> 1;}");
    PsiClass aClass = file.getClasses()[0];
    PsiField vField = aClass.getFields()[0];
    Object result = JavaPsiFacade.getInstance(getProject()).getConstantEvaluationHelper().computeConstantExpression(vField.getInitializer());

    assertEquals(2147483647, result);
  }
}
