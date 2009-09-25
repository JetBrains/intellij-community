/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.impl.ConcatenationToMessageFormatAction;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiExpression;
import com.intellij.testFramework.LightIdeaTestCase;

import java.util.ArrayList;

public class ConcatenationToMessageFormatActionTest extends LightIdeaTestCase {
  public void test1() throws Exception{
    final String text = "\"aaa 'bbb' '\" + ((java.lang.String)ccc) + \"'\"";
    final PsiExpression expression = JavaPsiFacade.getInstance(getProject()).getElementFactory().createExpressionFromText(
      text, null
    );
    final StringBuffer result = new StringBuffer();
    ConcatenationToMessageFormatAction.calculateFormatAndArguments(expression,
                                                                   result,
                                                                   new ArrayList<PsiExpression>(),
                                                                   new ArrayList<PsiExpression>(),
                                                                   false);

    assertEquals("aaa ''bbb'' ''{0}''", ConcatenationToMessageFormatAction.prepareString(result.toString()));
  }
}
