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
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.testFramework.LightResolveFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.LightResolveFixtureKt.lightResolveFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class TypeInferenceTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_1_5);
  private final TestFixture<LightResolveFixture> resolveFixture = lightResolveFixture(projectFixture);

  @Test
  public void testInferInCall1 () {
    doTestObject();
  }

  private void doTestObject() {
    PsiReferenceExpression ref = configure();
    ReadAction.runBlocking(() -> {
      PsiType type = ref.getType();
      assertTrue(type instanceof PsiClassType);
      PsiType[] paramTypes = ((PsiClassType)type).getParameters();
      assertEquals(1, paramTypes.length);
      assertEquals(CommonClassNames.JAVA_LANG_OBJECT, paramTypes[0].getCanonicalText());
    });
  }

  @Test
  public void testInferInAssign1 () {
    PsiReferenceExpression ref = configure();
    ReadAction.runBlocking(() -> {
      PsiType type = ref.getType();
      assertTrue(type instanceof PsiClassType);
      PsiType[] paramTypes = ((PsiClassType)type).getParameters();
      assertEquals(1, paramTypes.length);
      assertEquals( "java.lang.String", paramTypes[0].getCanonicalText());
    });
  }

  @Test
  public void testInferInAssign2() {
    doTestObject();
  }

  @Test
  public void testInferInCast () {
    doTestObject();
  }

  @Test
  public void testInferWithBounds () {
    checkResolvesTo("C.Inner");
  }

  @Test
  public void testInferWithBounds1 () {
    PsiReferenceExpression ref = configure();
    ReadAction.runBlocking(() -> {
      JavaResolveResult resolveResult = ref.advancedResolve(false);
      PsiSubstitutor substitutor = resolveResult.getSubstitutor();
      PsiMethod method = (PsiMethod)resolveResult.getElement();
      PsiType type = substitutor.substitute(method.getTypeParameters()[0]);
      assertEquals("java.lang.String", type.getCanonicalText());
    });
  }

  private PsiReferenceExpression configure() {
    return (PsiReferenceExpression)resolveFixture.get().findReferenceAtCaret("inference/" + resolveFixture.get().getTestName(false) + ".java");
  }

  @Test
  public void testInferInParamsOnly () {
    checkResolvesTo("C.I");
  }

  @Test
  public void testInferRawType () {
    checkResolvesTo(CommonClassNames.JAVA_LANG_OBJECT);
  }

  private void checkResolvesTo(@NonNls String typeName) {
    PsiReferenceExpression ref = configure();
    ReadAction.runBlocking(() -> {
      PsiType type = ref.getType();
      assertNotNull(type);
      assertEquals(typeName, type.getCanonicalText());
    });
  }

  @Test
  public void testInferInSuperAssignment () {
    checkResolvesTo("B<java.lang.String>");
  }

  @Test
  public void testInferWithWildcards () {
    checkResolvesTo("Collections.SelfComparable");
  }

  @Test
  public void testInferWithWildcards1 () {
    checkResolvesTo("java.lang.String");
  }

  @Test
  public void testInferWithWildcards2 () {
    checkResolvesTo("Collection<BarImpl>");
  }

  @Test
  public void testInferWithWildcards3 () {
    checkResolvesTo("X.Y<java.lang.Long>");
  }

  @Test
  public void testInferWithWildcards4 () {
    checkResolvesTo("X.Y<java.lang.Long>");
  }

  @Test
  public void testInferWithWildcards5 () {
    checkResolvesTo("X.Y<java.lang.Long>");
  }

  @Test
  public void testInferInReturn () {
    checkResolvesTo("T");
  }

  @Test
  public void testInferAutoboxed () {
    checkResolvesTo("java.lang.Integer");
  }

  @Test
  public void testInferWithVarargs1 () {
    checkResolvesTo("C2");
  }

  @Test
  public void testInferWithVarargs2 () {
    checkResolvesTo("C1");
  }

  @Test
  public void testInferWithVarargs3 () {
    checkResolvesTo("List<int[]>");
  }

  @Test
  public void testInferWithVarargs4 () {
    checkResolvesTo("List<int[]>");
  }

  @Test
  public void testInferWithVarargs5 () {
    checkResolvesTo("List<java.lang.Integer>");
  }

  @Test
  public void testInferWithVarargs6 () {
    checkResolvesTo("List<java.lang.Integer[]>");
  }

  @Test
  public void testInferPrimitiveArray () {
    checkResolvesTo("double[]");
  }

  @Test
  public void testSCR41031 () {
    checkResolvesTo("List<T>");
  }

  @Test
  public void testInferUnchecked () {
    checkResolvesTo(CommonClassNames.JAVA_LANG_OBJECT);
  }

  @Test
  public void testInferNotNull () {
    checkResolvesTo("E");
  }

  @Test
  public void testBoundComposition() {
    checkResolvesTo("java.lang.Class<? super ? extends java.lang.Object>");
  }
}
