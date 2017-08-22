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
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

public class InferredTypeTest extends LightCodeInsightFixtureTestCase {
  public void testNestedCallReturnType() {
    myFixture.configureByText("a.java", "import java.util.List;\n" +
                                        "abstract class Test {\n" +
                                        "    abstract <R, K> R foo(K k1, K k2);\n" +
                                        "    {\n" +
                                        "        String str = \"\";\n" +
                                        "        List<String> l = f<caret>oo(foo(str, str), str);\n" +
                                        "    }\n" +
                                        "}\n");
    final PsiElement elementAtCaret = myFixture.getFile().findElementAt(myFixture.getCaretOffset());
    Assert.assertTrue(elementAtCaret instanceof PsiIdentifier);

    final PsiElement refExpr = elementAtCaret.getParent();
    Assert.assertTrue(refExpr.toString(), refExpr instanceof PsiExpression);
    final PsiType type = ((PsiExpression)refExpr).getType();
    Assert.assertNotNull(refExpr.toString(), type);
    Assert.assertTrue(type.getCanonicalText(), type.equalsToText("java.util.List<java.lang.String>"));
  }

  public void testCashedTypes() {
    myFixture.configureByText("a.java", "import java.util.*;\n" +
                                        "abstract class Main {\n" +
                                        "    void test(List<Integer> li) {\n" +
                                        "       foo(li, s -> <caret>s.substr(0), Collections.emptyList());\n" +
                                        "    }\n" +
                                        "    abstract <T, U> Collection<U> foo(Collection<T> coll, Fun<Stream<T>, U> f, List<U> it);" +
                                        "    interface Stream<T> {\n" +
                                        "        T substr(long startingOffset);\n" +
                                        "    }\n" +
                                        "    interface Fun<T, R> {\n" +
                                        "        R _(T t);\n" +
                                        "    }\n" +
                                        "}\n");
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
    //noinspection UsePrimitiveTypes
    assertFalse(PsiType.VOID == method.getReturnType());
    myFixture.configureByText("a.java", "class A {{R r = () -> {};}} ");
    myFixture.checkHighlighting(false, false, false);
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }
}
