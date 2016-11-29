/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.codeInspection;

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.util.PsiConcatenationUtil;
import com.intellij.testFramework.LightIdeaTestCase;

import java.util.ArrayList;

public class ConcatenationToMessageFormatActionTest extends LightIdeaTestCase {

  public void doTest(String expressionText, String messageFormatText, String... foundExpressionTexts) {
    final PsiExpression expression = JavaPsiFacade.getElementFactory(getProject()).createExpressionFromText(expressionText, null);
    final StringBuilder result = new StringBuilder();
    final ArrayList<PsiExpression> args = new ArrayList<>();
    PsiConcatenationUtil.buildFormatString(expression, result, args, false);
    assertEquals(messageFormatText, result.toString());
    assertEquals(foundExpressionTexts.length, args.size());
    for (int i = 0; i < foundExpressionTexts.length; i++) {
      final String foundExpressionText = foundExpressionTexts[i];
      assertEquals(foundExpressionText, args.get(i).getText());
    }
  }
  
  public void test1() throws Exception{
    doTest("\"aaa 'bbb' '\" + ((java.lang.String)ccc) + \"'\"", "aaa ''bbb'' ''{0}''", "ccc");
  }
  
  public void test2() throws Exception {
    doTest("1 + 2 + 3 + \"{}'\" + '\\n' + ((java.lang.String)ccc)", "{0}'{}'''\\n{1}", "1 + 2 + 3", "ccc");
  }

  public void test3() throws Exception {
    doTest("\"Test{A = \" + 1 + \", B = \" + 2 + \", C = \" + 3 + \"}\"", "Test'{'A = {0}, B = {1}, C = {2}'}'", "1", "2", "3");
  }
}
