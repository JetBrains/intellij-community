/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.lambda;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

/**
 * User: anna
 */
public class PsiPolyExpressionUtilTest extends LightCodeInsightFixtureTestCase {
  public void testPrefixExpression() throws Exception {
    final PsiExpression psiExpression = findExpression("     int j = i<caret>++;");
    assertInstanceOf(psiExpression, PsiPostfixExpression.class);
    assertTrue(PsiPolyExpressionUtil.hasStandaloneForm(psiExpression));
    assertFalse(PsiPolyExpressionUtil.isPolyExpression(psiExpression));
  }

  public void testNumericConditionExpression() throws Exception {
    final PsiExpression psiExpression = findExpression("     int j = i == 0 <caret>? i + 1 : i - 1;");
    assertInstanceOf(psiExpression, PsiConditionalExpression.class);
    assertFalse(PsiPolyExpressionUtil.hasStandaloneForm(psiExpression));
    assertFalse(PsiPolyExpressionUtil.isPolyExpression(psiExpression));
  }

  public void testPolyConditionExpression() throws Exception {
    myFixture.configureByText("Foo.java", "import java.util.*;" +
                                          "class Foo {" +
                                          "  String foo(int i) {" +
                                          "     return i == 0 <caret>? bar() : bar();" +
                                          "  }" +
                                          "  String bar() {return null;}" +
                                          "}");
    final PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    assertNotNull(elementAtCaret);
    final PsiExpression psiExpression = PsiTreeUtil.getParentOfType(elementAtCaret, PsiExpression.class);
    assertInstanceOf(psiExpression, PsiConditionalExpression.class);
    assertFalse(PsiPolyExpressionUtil.hasStandaloneForm(psiExpression));
    assertTrue(PsiPolyExpressionUtil.isPolyExpression(psiExpression));
  }

  public void testNewExpressionDiamond() throws Exception {
    final PsiExpression psiExpression = findExpression("     List<String> l = new Arr<caret>ayList<>();");
    assertInstanceOf(psiExpression, PsiNewExpression.class);
    assertFalse(PsiPolyExpressionUtil.hasStandaloneForm(psiExpression));
    assertTrue(PsiPolyExpressionUtil.isPolyExpression(psiExpression));
  }

  public void testNewExpression() throws Exception {
    final PsiExpression psiExpression = findExpression("     List<String> l = new Arr<caret>ayList<String>();");
    assertInstanceOf(psiExpression, PsiNewExpression.class);
    assertFalse(PsiPolyExpressionUtil.hasStandaloneForm(psiExpression));
    assertFalse(PsiPolyExpressionUtil.isPolyExpression(psiExpression));
  }

  private PsiExpression findExpression(String textWithExpression) {
    myFixture.configureByText("Foo.java", "import java.util.*;" +
                                          "class Foo {" +
                                          "  void foo(int i) {" +
                                               textWithExpression +
                                          "  }" +
                                          "}");
    final PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    assertNotNull(elementAtCaret);
    return PsiTreeUtil.getParentOfType(elementAtCaret, PsiExpression.class);
  }

  public void testPertinentLambdaExpression() throws Exception {
    assertFalse(doTestLambdaPertinent("  void bar(List<Runnable> l) {" +
                                      "   foo(() <caret>-> {}, l);" +
                                      "  }"));
  }

  public void testPertinentImplicitLambdaExpression() throws Exception {
    assertFalse(doTestLambdaPertinent("  void bar(List<Comparable<String>> l) {" +
                                      "   foo((String s) <caret>-> 1, l);" +
                                      "  }"));
  }

  public void testPertinentNestedLambdaExpression() throws Exception {
    assertFalse(doTestLambdaPertinent("  interface Fun<I, O> { O inOut(I i);}\n" +
                                      "  void bar(List<Fun<String, Fun<String, String>>> l) {" +
                                      "   foo((sIn, sOut) -> (sInInner, sOutInner) <caret>-> sOutInner, l);" +
                                      "  }"));
  }

  private boolean doTestLambdaPertinent(final String barText) {
    myFixture.configureByText("Foo.java", "import java.util.*;" +
                                          "class Foo {" +
                                          "  <T> T foo(T t, List<T> lT) {" +
                                          "  }" +
                                          barText +
                                          "}");
    final PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    assertNotNull(elementAtCaret);
    final PsiExpression psiExpression = PsiTreeUtil.getParentOfType(elementAtCaret, PsiExpression.class);
    assertInstanceOf(psiExpression, PsiLambdaExpression.class);
    final PsiClass aClass = myFixture.findClass("Foo");
    assertNotNull(aClass);
    final PsiMethod[] meths = aClass.findMethodsByName("foo", false);
    assertTrue(meths.length == 1);
    return InferenceSession.isPertinentToApplicability(psiExpression, meths[0]);
  }
}
