/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  public void test1() {
    doTest("\"aaa 'bbb' '\" + ((java.lang.String)ccc) + \"'\"", "aaa ''bbb'' ''{0}''", "ccc");
  }

  public void test2() {
    doTest("1 + 2 + 3 + \"{}'\" + '\\n' + ((java.lang.String)ccc)", "{0}'{}'''\\n{1}", "1 + 2 + 3", "ccc");
  }

  public void test3() {
    doTest("\"Test{A = \" + 1 + \", B = \" + 2 + \", C = \" + 3 + \"}\"", "Test'{'A = {0}, B = {1}, C = {2}'}'", "1", "2", "3");
  }

  public void testNullCast() {
    doTest("\"abc\" + (String)()", "abc{0}", "(String)()");
  }
}
