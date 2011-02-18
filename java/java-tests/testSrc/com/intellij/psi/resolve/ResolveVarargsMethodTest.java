/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.psi.resolve;

import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;

import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.Assert.assertThat;

public class ResolveVarargsMethodTest extends Resolve15TestCase {

  public void testPrimitiveObject() throws Exception {
    doTest(1);
  }

  public void testPrimitiveString() throws Exception {
    doTest(2);
  }

  public void testObjectObject() throws Exception {
    doTest(2);
  }

  //bug in javac; @see jdk7
  public void testObjectObjectObject() throws Exception {
    doTest(2);
  }

  public void testStringObject() throws Exception {
    doTest(1);
  }

  public void testStringObjectString() throws Exception {
    doTest(1);
  }

  public void testStringStringString() throws Exception {
    doTest(2);
  }

  private void doTest(int resolved) throws Exception {
    final PsiReference ref = configureByFile();
    assertThat(ref, instanceOf(PsiReferenceExpression.class));
    final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
    PsiCallExpression call = (PsiCallExpression) refExpr.getParent();
    JavaResolveResult resolveResult = call.resolveMethodGenerics();
    PsiElement element = resolveResult.getElement();
    if (resolved == 1) {
      assertNotNull(element);
    } else {
      assertNull(element);
    }
    final JavaResolveResult[] candidates = refExpr.multiResolve(false);
    assertEquals(resolved, candidates.length);
  }

  private PsiReference configureByFile() throws Exception {
    return configureByFile("method/varargs/" + getTestName(false) + ".java");
  }
  private static PsiMethod checkResolvesUnique(final PsiReference ref) {
    assertThat(ref, instanceOf(PsiReferenceExpression.class));
    final PsiReferenceExpression refExpr = (PsiReferenceExpression)ref;
    final PsiElement parent = refExpr.getParent();
    assertThat(parent, instanceOf(PsiMethodCallExpression.class));
    final PsiMethodCallExpression expression = (PsiMethodCallExpression)parent;
    final PsiMethod method = expression.resolveMethod();
    assertNotNull(method);
    return method;
  }

  private static void assertResolvesToMethodInClass(JavaResolveResult result, @NonNls String name) {
    PsiMethod method = (PsiMethod)result.getElement();
    assertNotNull(method);
    assertTrue(result.isValidResult());
    assertEquals(name, method.getContainingClass().getName());
  }
}
