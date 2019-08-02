// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.intention;

import com.intellij.JavaTestUtil;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.util.PsiConcatenationUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

import java.util.ArrayList;

public class ConcatenationToMessageFormatTest extends JavaCodeInsightFixtureTestCase {

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/concatenationToMessageFormat/";
  }

  public void testConstant() {
    assertTestNotAvailable();
  }

  public void testSimple() {
    doTest();
  }

  public void testComments() { doTest(); }
  public void testTextblock() { doTest(); }
  public void testEscaping() { doTest(); }

  private void doTest() {
    final String name = getTestName(true);
    CodeInsightTestUtil
      .doIntentionTest(myFixture, "Replace '+' with 'java.text.MessageFormat.format()'", name + ".java", name + ".after.java");
  }

  private void assertTestNotAvailable() {
    myFixture.configureByFile(getTestName(true) + ".java");
    assertEmpty(myFixture.filterAvailableIntentions("Replace '+' with 'java.text.MessageFormat.format()'"));
  }

  private void doTest(String expressionText, String messageFormatText, String... foundExpressionTexts) {
    final PsiExpression expression = getElementFactory().createExpressionFromText(expressionText, null);
    final ArrayList<PsiExpression> args = new ArrayList<>();
    final String formatString = PsiConcatenationUtil.buildUnescapedFormatString(expression, false, args);
    assertEquals(messageFormatText, formatString);
    assertEquals(foundExpressionTexts.length, args.size());
    for (int i = 0; i < foundExpressionTexts.length; i++) {
      assertEquals(foundExpressionTexts[i], args.get(i).getText());
    }
  }

  public void test1() {
    doTest("\"aaa 'bbb' '\" + ((java.lang.String)ccc) + \"'\"", "aaa ''bbb'' ''{0}''", "ccc");
  }

  public void test2() {
    doTest("1 + 2 + 3 + \"{}'\" + '\\n' + ((java.lang.String)ccc)", "{0}'{}'''\n{1}", "1 + 2 + 3", "ccc");
  }

  public void test3() {
    doTest("\"Test{A = \" + 1 + \", B = \" + 2 + \", C = \" + 3 + \"}\"", "Test'{'A = {0}, B = {1}, C = {2}'}'", "1", "2", "3");
  }

  public void testNullCast() {
    doTest("\"abc\" + (String)()", "abc{0}", "(String)()");
  }
}
