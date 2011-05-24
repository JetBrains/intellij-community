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
    final StringBuilder result = new StringBuilder();
    ConcatenationToMessageFormatAction.buildMessageFormatString(expression,
                                                                result,
                                                                new ArrayList<PsiExpression>());
    assertEquals("aaa ''bbb'' ''{0}''", result.toString());
  }
  
  public void test2() throws Exception {
    final String text = "1 + 2 + 3 + \"{}'\" + '\\n' + ((java.lang.String)ccc)";
    final PsiExpression expression = JavaPsiFacade.getElementFactory(getProject()).createExpressionFromText(text, null);
    final StringBuilder result = new StringBuilder();
    final ArrayList<PsiExpression> args = new ArrayList<PsiExpression>();
    ConcatenationToMessageFormatAction.buildMessageFormatString(expression, result, args);
    assertEquals("{0}'{'}''\\n{1}", result.toString());
    assertEquals(2, args.size());
    assertEquals("1 + 2 + 3", args.get(0).getText());
    assertEquals("ccc", args.get(1).getText());
  }
}
