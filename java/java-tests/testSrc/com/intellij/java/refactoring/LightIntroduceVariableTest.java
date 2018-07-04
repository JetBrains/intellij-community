// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

import java.util.List;

public class LightIntroduceVariableTest extends LightCodeInsightFixtureTestCase {
  public void testExpressionsUnderCaret() {
    PsiFile file = myFixture.configureByText(StdFileTypes.JAVA, "package a; class A {{new Double(0.<caret>)}}");
    List<PsiExpression> expressions =
      IntroduceVariableBase.collectExpressions(file, myFixture.getEditor(), myFixture.getCaretOffset(), false);
    assertSize(2, expressions);
  }
}
