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

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiConditionalExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiPostfixExpression;
import com.intellij.psi.impl.source.resolve.graphInference.InferenceSession;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class PsiPolyExpressionUtilTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testPrefixExpression() {
    final PsiExpression psiExpression = findExpression("     int j = i<caret>++;");
    ReadAction.runBlocking(() -> {
      assertInstanceOf(PsiPostfixExpression.class, psiExpression);
      assertTrue(PsiPolyExpressionUtil.hasStandaloneForm(psiExpression));
      assertFalse(PsiPolyExpressionUtil.isPolyExpression(psiExpression));
    });
  }

  @Test
  public void testNumericConditionExpression() {
    final PsiExpression psiExpression = findExpression("     int j = i == 0 <caret>? i + 1 : i - 1;");
    ReadAction.runBlocking(() -> {
      assertInstanceOf(PsiConditionalExpression.class, psiExpression);
      assertFalse(PsiPolyExpressionUtil.hasStandaloneForm(psiExpression));
      assertFalse(PsiPolyExpressionUtil.isPolyExpression(psiExpression));
    });
  }

  @Test
  public void testPolyConditionExpression() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Foo.java", "import java.util.*;" +
                                          "class Foo {" +
                                          "  String foo(int i) {" +
                                          "     return i == 0 <caret>? bar() : bar();" +
                                          "  }" +
                                          "  String bar() {return null;}" +
                                          "}");
    ReadAction.runBlocking(() -> {
      final PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
      assertNotNull(elementAtCaret);
      final PsiExpression psiExpression = PsiTreeUtil.getParentOfType(elementAtCaret, PsiExpression.class);
      assertInstanceOf(PsiConditionalExpression.class, psiExpression);
      assertFalse(PsiPolyExpressionUtil.hasStandaloneForm(psiExpression));
      assertTrue(PsiPolyExpressionUtil.isPolyExpression(psiExpression));
    });
  }

  @Test
  public void testNewExpressionDiamond() {
    final PsiExpression psiExpression = findExpression("     List<String> l = new Arr<caret>ayList<>();");
    ReadAction.runBlocking(() -> {
      assertInstanceOf(PsiNewExpression.class, psiExpression);
      assertFalse(PsiPolyExpressionUtil.hasStandaloneForm(psiExpression));
      assertTrue(PsiPolyExpressionUtil.isPolyExpression(psiExpression));
    });
  }

  @Test
  public void testNewExpression() {
    final PsiExpression psiExpression = findExpression("     List<String> l = new Arr<caret>ayList<String>();");
    ReadAction.runBlocking(() -> {
      assertInstanceOf(PsiNewExpression.class, psiExpression);
      assertFalse(PsiPolyExpressionUtil.hasStandaloneForm(psiExpression));
      assertFalse(PsiPolyExpressionUtil.isPolyExpression(psiExpression));
    });
  }

  @Test
  public void testMethodCallInsideArrayCreation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Foo.java", "import java.util.*;" +
                                          "class Foo {" +
                                          "  <T> T bar() {return null;}" +
                                          "  void foo() {" +
                                          "    String[] a = new String[] {ba<caret>r()};" +
                                          "  }" +
                                          "}");
    ReadAction.runBlocking(() -> {
      final PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
      assertNotNull(elementAtCaret);
      final PsiExpression psiExpression = PsiTreeUtil.getParentOfType(elementAtCaret, PsiMethodCallExpression.class);
      assertInstanceOf(PsiMethodCallExpression.class, psiExpression);
      assertTrue(PsiPolyExpressionUtil.isPolyExpression(psiExpression));
    });
  }

  @Test
  public void testConditional() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Foo.java", """
      import java.util.function.Supplier;class Foo {    private static <R> void map(Supplier<R> fn) {}
          public static void main(String[] args) {
              Runnable r = null;
              map(() -> (true <caret>? r : r));
          }}""");
    ReadAction.runBlocking(() -> {
      final PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
      assertNotNull(elementAtCaret);
      final PsiExpression psiExpression = PsiTreeUtil.getParentOfType(elementAtCaret, PsiExpression.class);
      assertInstanceOf(PsiConditionalExpression.class, psiExpression);
      assertTrue(PsiPolyExpressionUtil.isPolyExpression(psiExpression));
    });
  }

  @Test
  public void testConditionalInAssignment() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Foo.java", """
      class Foo {    public static void main(String[] args) {
              Object obj = new Object();
              String str = "";
              str += args.length == 0 <caret>? obj : args[0];
          }}""");
    ReadAction.runBlocking(() -> {
      final PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
      assertNotNull(elementAtCaret);
      final PsiExpression psiExpression = PsiTreeUtil.getParentOfType(elementAtCaret, PsiExpression.class);
      assertInstanceOf(PsiConditionalExpression.class, psiExpression);
      assertFalse(PsiPolyExpressionUtil.isPolyExpression(psiExpression));
    });
  }

  private PsiExpression findExpression(String textWithExpression) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Foo.java", "import java.util.*;" +
                                          "class Foo {" +
                                          "  void foo(int i) {" +
                                               textWithExpression +
                                          "  }" +
                                          "}");
    return ReadAction.computeBlocking(() -> {
      final PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
      assertNotNull(elementAtCaret);
      return PsiTreeUtil.getParentOfType(elementAtCaret, PsiExpression.class);
    });
  }

  @Test
  public void testPertinentLambdaExpression() {
    assertFalse(doTestLambdaPertinent("  void bar(List<Runnable> l) {" +
                                      "   foo(() <caret>-> {}, l);" +
                                      "  }"));
  }

  @Test
  public void testPertinentImplicitLambdaExpression() {
    assertFalse(doTestLambdaPertinent("  void bar(List<Comparable<String>> l) {" +
                                      "   foo((String s) <caret>-> 1, l);" +
                                      "  }"));
  }

  @Test
  public void testPertinentNestedLambdaExpression() {
    assertFalse(doTestLambdaPertinent("  interface Fun<I, O> { O inOut(I i);}\n" +
                                      "  void bar(List<Fun<String, Fun<String, String>>> l) {" +
                                      "   foo((sIn, sOut) -> (sInInner, sOutInner) <caret>-> sOutInner, l);" +
                                      "  }"));
  }

  @Test
  public void testPertinentNestedLambdaExpressionWhenTargetIsTypeParameterOfMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Foo.java", """
      interface Supplier<T> { T get();}class Foo {      { Supplier<Runnable> x = foo ((<caret>) -> () -> {});}
            static <T> Supplier<T> foo(Supplier<T> delegate) {
                return null;
            }
      }""");
    ReadAction.runBlocking(() -> {
      final PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
      assertNotNull(elementAtCaret);
      final PsiExpression psiExpression = PsiTreeUtil.getParentOfType(elementAtCaret, PsiExpression.class);
      assertInstanceOf(PsiLambdaExpression.class, psiExpression);
      final PsiClass aClass = myFixture.findClass("Foo");
      assertNotNull(aClass);
      final PsiMethod[] meths = aClass.findMethodsByName("foo", false);
      assertEquals(1, meths.length);
      assertFalse(InferenceSession.isPertinentToApplicability(psiExpression, meths[0]));
    });
  }

  @Test
  public void testNotExactMethodReferenceOnRawClassType() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Foo.java", "class Foo {" +
                                          "    class Test<A>{ Test(){}}" +
                                          "    {Runnable r = Test:<caret>:new;}" +  
                                          "}");
    ReadAction.runBlocking(() -> {
      final PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
      assertNotNull(elementAtCaret);
      final PsiExpression psiExpression = PsiTreeUtil.getParentOfType(elementAtCaret, PsiExpression.class);
      assertInstanceOf(PsiMethodReferenceExpression.class, psiExpression);
      assertFalse(((PsiMethodReferenceExpression)psiExpression).isExact());
    });
  }

  @Test
  public void testExactMethodReferenceOnGenericClassType() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Foo.java", "class Foo {" +
                                          "    class Test<A>{ Test(){}}" +
                                          "    {Runnable r = Test<String>:<caret>:new;}" +  
                                          "}");
    ReadAction.runBlocking(() -> {
      final PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
      assertNotNull(elementAtCaret);
      final PsiExpression psiExpression = PsiTreeUtil.getParentOfType(elementAtCaret, PsiExpression.class);
      assertInstanceOf(PsiMethodReferenceExpression.class, psiExpression);
      assertTrue(((PsiMethodReferenceExpression)psiExpression).isExact());
    });
  }

  private boolean doTestLambdaPertinent(final String barText) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("Foo.java", "import java.util.*;" +
                                          "class Foo {" +
                                          "  <T> T foo(T t, List<T> lT) {" +
                                          "  }" +
                                          barText +
                                          "}");
    return ReadAction.computeBlocking(() -> {
      final PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
      assertNotNull(elementAtCaret);
      final PsiExpression psiExpression = PsiTreeUtil.getParentOfType(elementAtCaret, PsiExpression.class);
      assertInstanceOf(PsiLambdaExpression.class, psiExpression);
      final PsiClass aClass = myFixture.findClass("Foo");
      assertNotNull(aClass);
      final PsiMethod[] meths = aClass.findMethodsByName("foo", false);
      assertEquals(1, meths.length);
      return InferenceSession.isPertinentToApplicability(psiExpression, meths[0]);
    });
  }
}
