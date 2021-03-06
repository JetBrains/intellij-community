// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring.inline;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPatternVariable;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.refactoring.inline.InlineLocalHandler;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class InlinePatternVariableTest extends LightJavaCodeInsightTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }
  
  public void testSimple() { doTest(); }
  public void testSimpleAtRef() { doTest(); }
  public void testTernary() { doTest(); }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LightJavaCodeInsightFixtureTestCase.JAVA_15;
  }

  private void doTest() {
    String name = getTestName(false);
    String fileName = "/refactoring/inlinePatternVariable/" + name + ".java";
    configureByFile(fileName);
    PsiElement element = TargetElementUtil
      .findTargetElement(getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED | TargetElementUtil.REFERENCED_ELEMENT_ACCEPTED);
    if (element instanceof PsiPatternVariable) {
      InlineLocalHandler.inlineVariable(getProject(), getEditor(), (PsiPatternVariable)element, null);
    } else {
      assertTrue(element instanceof PsiReferenceExpression);
      PsiPatternVariable patternVariable = (PsiPatternVariable)((PsiReferenceExpression)element).resolve();
      assertNotNull(patternVariable);
      InlineLocalHandler.inlineVariable(getProject(), getEditor(), patternVariable, (PsiReferenceExpression)element);
    }
    checkResultByFile(fileName + ".after");
  }
}
