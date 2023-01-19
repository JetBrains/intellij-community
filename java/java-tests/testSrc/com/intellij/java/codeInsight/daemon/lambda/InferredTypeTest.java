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
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class InferredTypeTest extends LightJavaCodeInsightFixtureTestCase {
  public void testNestedCallReturnType() {
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
    final PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    Assert.assertTrue(elementAtCaret instanceof PsiIdentifier);

    final PsiElement refExpr = elementAtCaret.getParent();
    Assert.assertTrue(refExpr.toString(), refExpr instanceof PsiExpression);
    final PsiType type = ((PsiExpression)refExpr).getType();
    Assert.assertNotNull(refExpr.toString(), type);
    Assert.assertTrue(type.getCanonicalText(), type.equalsToText("java.util.List<java.lang.String>"));
  }

  public void testCashedTypes() {
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
    final PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    Assert.assertTrue(elementAtCaret instanceof PsiIdentifier);

    final PsiElement refExpr = elementAtCaret.getParent();
    Assert.assertTrue(refExpr.toString(), refExpr instanceof PsiExpression);
    final PsiType type = ((PsiExpression)refExpr).getType();
    Assert.assertNotNull(refExpr.toString(), type);
    Assert.assertTrue(type.getCanonicalText(), type.equalsToText("Stream<java.lang.Integer>"));

    final PsiExpressionList expressionList = PsiTreeUtil.getParentOfType(refExpr, PsiExpressionList.class);
    assertNotNull(expressionList);
    final PsiExpression[] expressions = expressionList.getExpressions();
    assertEquals(3, expressions.length);

    final PsiType ensureNotCached = expressions[2].getType();
    assertNotNull(ensureNotCached);
    assertTrue(ensureNotCached.getCanonicalText(), ensureNotCached.equalsToText("java.util.List<java.lang.Integer>"));
  }

  public void testAnnotatedVoidReturnType() {
    myFixture.addClass("@java.lang.annotation.Target(value={java.lang.annotation.ElementType.TYPE_USE}) @interface D {}");
    final PsiJavaFile file = (PsiJavaFile)myFixture.addFileToProject("R.java", "public interface R {@D void run();}");
    final PsiClass psiClass = file.getClasses()[0];
    final PsiMethod method = psiClass.getMethods()[0];
    assertNotSame(PsiType.VOID, method.getReturnType());
    myFixture.configureByText("a.java", "class A {{R r = () -> {};}} ");
    myFixture.checkHighlighting(false, false, false);
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testNestedDiamondsInsideAssignmentInMethodsCall() throws IOException {
    String path = Diamond8HighlightingTest.BASE_PATH + "/" + getTestName(false) + ".java";
    
    PsiFile file = myFixture.configureByFile(path);
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
      assertEquals(get.getText() +
                   " with qualifier resolving to " + ((PsiReferenceExpression)supplier).resolve() +
                   " of type " + (supplierType == null ? null : supplierType.getCanonicalText()),
                   "Double", getType);
    }
    assertEquals("C<Double>", newC.getType().getPresentableText());
    assertEquals("Function<Supplier<Double>, Double>", lambda.getFunctionalInterfaceType().getPresentableText());

    checkResolveResultDoesNotDependOnResolveOrder(file);
  }

  private void checkResolveResultDoesNotDependOnResolveOrder(PsiFile file) {
    Map<PsiJavaCodeReferenceElement, String> gold = new HashMap<>();
    for (PsiJavaCodeReferenceElement ref : SyntaxTraverser.psiTraverser(file).filter(PsiJavaCodeReferenceElement.class)) {
      gold.put(ref, ref.advancedResolve(true).toString());
    }

    for (PsiJavaCodeReferenceElement ref : gold.keySet()) {
      getPsiManager().dropPsiCaches();
      assertEquals("Wrong resolve result for " + ref.getText(), gold.get(ref), ref.advancedResolve(true).toString());
    }
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }
}
