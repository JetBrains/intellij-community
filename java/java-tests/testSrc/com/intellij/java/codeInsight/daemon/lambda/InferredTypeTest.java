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

import com.intellij.JavaTestUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class InferredTypeTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath());
  }

  @Test
  public void testNestedCallReturnType() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
      import java.util.List;
      abstract class Test {
          abstract <R, K> R foo(K k1, K k2);
          {
              String str = "";
              List<String> l = f<caret>oo(foo(str, str), str);
          }
      }
      """);
    ReadAction.runBlocking(() -> {
      final PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
      assertInstanceOf(PsiIdentifier.class, elementAtCaret);

      final PsiElement refExpr = elementAtCaret.getParent();
      assertInstanceOf(PsiExpression.class, refExpr, refExpr.toString());
      final PsiType type = ((PsiExpression)refExpr).getType();
      assertNotNull(type, refExpr.toString());
      assertTrue(type.equalsToText("java.util.List<java.lang.String>"), type.getCanonicalText());
    });
  }

  @Test
  public void testCashedTypes() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
      import java.util.*;
      abstract class Main {
          void test(List<Integer> li) {
             foo(li, s -> <caret>s.substr(0), Collections.emptyList());
          }
          abstract <T, U> Collection<U> foo(Collection<T> coll, Fun<Stream<T>, U> f, List<U> it);    interface Stream<T> {
              T substr(long startingOffset);
          }
          interface Fun<T, R> {
              R _(T t);
          }
      }
      """);
    ReadAction.runBlocking(() -> {
      final PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
      assertInstanceOf(PsiIdentifier.class, elementAtCaret);

      final PsiElement refExpr = elementAtCaret.getParent();
      assertInstanceOf(PsiExpression.class, refExpr, refExpr.toString());
      final PsiType type = ((PsiExpression)refExpr).getType();
      assertNotNull(type, refExpr.toString());
      assertTrue(type.equalsToText("Stream<java.lang.Integer>"), type.getCanonicalText());

      final PsiExpressionList expressionList = PsiTreeUtil.getParentOfType(refExpr, PsiExpressionList.class);
      assertNotNull(expressionList);
      final PsiExpression[] expressions = expressionList.getExpressions();
      assertEquals(3, expressions.length);

      final PsiType ensureNotCached = expressions[2].getType();
      assertNotNull(ensureNotCached);
      assertTrue(ensureNotCached.equalsToText("java.util.List<java.lang.Integer>"), ensureNotCached.getCanonicalText());
    });
  }

  @Test
  public void testAnnotatedVoidReturnType() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("@java.lang.annotation.Target(value={java.lang.annotation.ElementType.TYPE_USE}) @interface D {}");
    final PsiJavaFile file = (PsiJavaFile)myFixture.addFileToProject("R.java", "public interface R {@D void run();}");
    ReadAction.runBlocking(() -> {
      final PsiClass psiClass = file.getClasses()[0];
      final PsiMethod method = psiClass.getMethods()[0];
      assertNotSame(PsiTypes.voidType(), method.getReturnType());
    });
    myFixture.configureByText("a.java", "class A {{R r = () -> {};}} ");
    myFixture.checkHighlighting(false, false, false);
  }

  @Test
  public void testNestedDiamondsInsideAssignmentInMethodsCall() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String path = Diamond8HighlightingTest.BASE_PATH + "/" + myFixture.getTestName(false) + ".java";
    
    PsiFile file = myFixture.configureByFile(path);
    ReadAction.runBlocking(() -> {
      String text = file.getText();

      PsiNewExpression newB =
        PsiTreeUtil.findElementOfClassAtOffset(file, text.indexOf("new B<>"), PsiNewExpression.class, false);

      PsiNewExpression newC =
        PsiTreeUtil.findElementOfClassAtOffset(file, text.indexOf("new C<>"), PsiNewExpression.class, false);

      PsiMethodCallExpression get =
        PsiTreeUtil.findElementOfClassAtOffset(file, text.indexOf("get()"), PsiMethodCallExpression.class, false);

      PsiLambdaExpression lambda =
        PsiTreeUtil.findElementOfClassAtOffset(file, text.indexOf("->"), PsiLambdaExpression.class, false);

      assertEquals("B<Double>", newB.getType().getPresentableText());
      String getType = get.getType().getPresentableText();
      if (!"Double".equals(getType)) {
        PsiExpression supplier = get.getMethodExpression().getQualifierExpression();
        PsiType supplierType = supplier.getType();
        assertEquals("Double", getType,
                     get.getText() +
                     " with qualifier resolving to " + ((PsiReferenceExpression)supplier).resolve() +
                     " of type " + (supplierType == null ? null : supplierType.getCanonicalText()));
      }
      assertEquals("C<Double>", newC.getType().getPresentableText());
      assertEquals("Function<Supplier<Double>, Double>", lambda.getFunctionalInterfaceType().getPresentableText());
    });

    EdtTestUtil.runInEdtAndWait(() -> checkResolveResultDoesNotDependOnResolveOrder(file));
  }

  private static void checkResolveResultDoesNotDependOnResolveOrder(PsiFile file) {
    Map<PsiJavaCodeReferenceElement, String> gold = new HashMap<>();
    for (PsiJavaCodeReferenceElement ref : SyntaxTraverser.psiTraverser(file).filter(PsiJavaCodeReferenceElement.class)) {
      gold.put(ref, ref.advancedResolve(true).toString());
    }

    for (PsiJavaCodeReferenceElement ref : gold.keySet()) {
      file.getManager().dropPsiCaches();
      assertEquals(gold.get(ref), ref.advancedResolve(true).toString(), "Wrong resolve result for " + ref.getText());
    }
  }
}
