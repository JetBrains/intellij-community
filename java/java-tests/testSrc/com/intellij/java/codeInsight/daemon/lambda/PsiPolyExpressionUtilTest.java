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
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class PsiPolyExpressionUtilTest extends LightCodeInsightFixtureTestCase {
  public void testPrefixExpression() {
    final PsiExpression psiExpression = findExpression("     int j = i<caret>++;");
    assertInstanceOf(psiExpression, PsiPostfixExpression.class);
    assertTrue(PsiPolyExpressionUtil.hasStandaloneForm(psiExpression));
    assertFalse(PsiPolyExpressionUtil.isPolyExpression(psiExpression));
  }

  public void testNumericConditionExpression() {
    final PsiExpression psiExpression = findExpression("     int j = i == 0 <caret>? i + 1 : i - 1;");
    assertInstanceOf(psiExpression, PsiConditionalExpression.class);
    assertFalse(PsiPolyExpressionUtil.hasStandaloneForm(psiExpression));
    assertFalse(PsiPolyExpressionUtil.isPolyExpression(psiExpression));
  }

  public void testPolyConditionExpression() {
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

  public void testNewExpressionDiamond() {
    final PsiExpression psiExpression = findExpression("     List<String> l = new Arr<caret>ayList<>();");
    assertInstanceOf(psiExpression, PsiNewExpression.class);
    assertFalse(PsiPolyExpressionUtil.hasStandaloneForm(psiExpression));
    assertTrue(PsiPolyExpressionUtil.isPolyExpression(psiExpression));
  }

  public void testNewExpression() {
    final PsiExpression psiExpression = findExpression("     List<String> l = new Arr<caret>ayList<String>();");
    assertInstanceOf(psiExpression, PsiNewExpression.class);
    assertFalse(PsiPolyExpressionUtil.hasStandaloneForm(psiExpression));
    assertFalse(PsiPolyExpressionUtil.isPolyExpression(psiExpression));
  }

  public void testMethodCallInsideArrayCreation() {
    myFixture.configureByText("Foo.java", "import java.util.*;" +
                                          "class Foo {" +
                                          "  <T> T bar() {return null;}" +
                                          "  void foo() {" +
                                          "    String[] a = new String[] {ba<caret>r()};" +
                                          "  }" +
                                          "}");
    final PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    assertNotNull(elementAtCaret);
    final PsiExpression psiExpression = PsiTreeUtil.getParentOfType(elementAtCaret, PsiMethodCallExpression.class);
    assertInstanceOf(psiExpression, PsiMethodCallExpression.class);
    assertTrue(PsiPolyExpressionUtil.isPolyExpression(psiExpression));
  }

  public void testConditional() {
    myFixture.configureByText("Foo.java", "import java.util.function.Supplier;" +
                                          "class Foo {" +
                                          "    private static <R> void map(Supplier<R> fn) {}\n" +
                                          "    public static void main(String[] args) {\n" +
                                          "        Runnable r = null;\n" +
                                          "        map(() -> (true <caret>? r : r));\n" +
                                          "    }" +
                                          "}");
    final PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    assertNotNull(elementAtCaret);
    final PsiExpression psiExpression = PsiTreeUtil.getParentOfType(elementAtCaret, PsiExpression.class);
    assertInstanceOf(psiExpression, PsiConditionalExpression.class);
    assertTrue(PsiPolyExpressionUtil.isPolyExpression(psiExpression));
  }

  public void testConditionalInAssignment() {
    myFixture.configureByText("Foo.java", "class Foo {" +
                                          "    public static void main(String[] args) {\n" +
                                          "        Object obj = new Object();\n" +
                                          "        String str = \"\";\n" +
                                          "        str += args.length == 0 <caret>? obj : args[0];\n" +
                                          "    }" +
                                          "}");
    final PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    assertNotNull(elementAtCaret);
    final PsiExpression psiExpression = PsiTreeUtil.getParentOfType(elementAtCaret, PsiExpression.class);
    assertInstanceOf(psiExpression, PsiConditionalExpression.class);
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

  public void testPertinentLambdaExpression() {
    assertFalse(doTestLambdaPertinent("  void bar(List<Runnable> l) {" +
                                      "   foo(() <caret>-> {}, l);" +
                                      "  }"));
  }

  public void testPertinentImplicitLambdaExpression() {
    assertFalse(doTestLambdaPertinent("  void bar(List<Comparable<String>> l) {" +
                                      "   foo((String s) <caret>-> 1, l);" +
                                      "  }"));
  }

  public void testPertinentNestedLambdaExpression() {
    assertFalse(doTestLambdaPertinent("  interface Fun<I, O> { O inOut(I i);}\n" +
                                      "  void bar(List<Fun<String, Fun<String, String>>> l) {" +
                                      "   foo((sIn, sOut) -> (sInInner, sOutInner) <caret>-> sOutInner, l);" +
                                      "  }"));
  }

  public void testPertinentNestedLambdaExpressionWhenTargetIsTypeParameterOfMethod() {
    myFixture.configureByText("Foo.java", "interface Supplier<T> { T get();}" +
                                          "class Foo {" +
                                          "      { Supplier<Runnable> x = foo ((<caret>) -> () -> {});}\n" +
                                          "      static <T> Supplier<T> foo(Supplier<T> delegate) {\n" +
                                          "          return null;\n" +
                                          "      }\n" +
                                          "}");
    final PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    assertNotNull(elementAtCaret);
    final PsiExpression psiExpression = PsiTreeUtil.getParentOfType(elementAtCaret, PsiExpression.class);
    assertInstanceOf(psiExpression, PsiLambdaExpression.class);
    final PsiClass aClass = myFixture.findClass("Foo");
    assertNotNull(aClass);
    final PsiMethod[] meths = aClass.findMethodsByName("foo", false);
    assertTrue(meths.length == 1);
    assertFalse(InferenceSession.isPertinentToApplicability(psiExpression, meths[0]));
  }

  public void testNotExactMethodReferenceOnRawClassType() {
    myFixture.configureByText("Foo.java", "class Foo {" +
                                          "    class Test<A>{ Test(){}}" +
                                          "    {Runnable r = Test:<caret>:new;}" +  
                                          "}");
    final PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    assertNotNull(elementAtCaret);
    final PsiExpression psiExpression = PsiTreeUtil.getParentOfType(elementAtCaret, PsiExpression.class);
    assertInstanceOf(psiExpression, PsiMethodReferenceExpression.class);
    assertFalse(((PsiMethodReferenceExpression)psiExpression).isExact());
  }

  public void testExactMethodReferenceOnGenericClassType() {
    myFixture.configureByText("Foo.java", "class Foo {" +
                                          "    class Test<A>{ Test(){}}" +
                                          "    {Runnable r = Test<String>:<caret>:new;}" +  
                                          "}");
    final PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    assertNotNull(elementAtCaret);
    final PsiExpression psiExpression = PsiTreeUtil.getParentOfType(elementAtCaret, PsiExpression.class);
    assertInstanceOf(psiExpression, PsiMethodReferenceExpression.class);
    assertTrue(((PsiMethodReferenceExpression)psiExpression).isExact());
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
