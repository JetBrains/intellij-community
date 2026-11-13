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
package com.intellij.java.psi.resolve;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiCallExpression;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.testFramework.LightResolveFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.LightResolveFixtureKt.lightResolveFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class ResolveVarargsMethodTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_1_5);
  private final TestFixture<LightResolveFixture> resolveFixture = lightResolveFixture(projectFixture);

  @Test
  public void testPrimitiveObject() throws Exception {
    doTest(1);
  }

  @Test
  public void testPrimitiveString() throws Exception {
    doTest(2);
  }

  @Test
  public void testObjectObject() throws Exception {
    doTest(2);
  }

  //bug in javac; @see jdk7
  @Test
  public void testObjectObjectObject() throws Exception {
    doTest(2);
  }

  @Test
  public void testStringObject() throws Exception {
    doTest(1);
  }

  @Test
  public void testStringObjectString() throws Exception {
    doTest(1);
  }

  @Test
  public void testStringStringString() throws Exception {
    doTest(2);
  }

  private void doTest(int resolved) throws Exception {
    final PsiReference ref = configureByFile();
    ReadAction.runBlocking(() -> {
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
    });
  }

  private PsiReference configureByFile() throws Exception {
    return resolveFixture.get().findReferenceAtCaret("method/varargs/" + resolveFixture.get().getTestName(false) + ".java");
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
