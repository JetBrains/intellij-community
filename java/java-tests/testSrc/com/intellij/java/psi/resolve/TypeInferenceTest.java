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

import com.intellij.psi.*;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.LightResolveTestCase;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class TypeInferenceTest extends LightResolveTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_1_5;
  }

  public void testInferInCall1 () {
    doTestObject();
  }

  private void doTestObject() {
    PsiReferenceExpression ref = configure();
    PsiType type = ref.getType();
    assertTrue(type instanceof PsiClassType);
    PsiType[] paramTypes = ((PsiClassType)type).getParameters();
    assertEquals(1, paramTypes.length);
    assertEquals(CommonClassNames.JAVA_LANG_OBJECT, paramTypes[0].getCanonicalText());
  }

  public void testInferInAssign1 () {
    PsiReferenceExpression ref = configure();
    PsiType type = ref.getType();
    assertTrue(type instanceof PsiClassType);
    PsiType[] paramTypes = ((PsiClassType)type).getParameters();
    assertEquals(1, paramTypes.length);
    assertEquals( "java.lang.String", paramTypes[0].getCanonicalText());
  }

  public void testInferInAssign2() {
    doTestObject();
  }

  public void testInferInCast () {
    doTestObject();
  }

  public void testInferWithBounds () {
    checkResolvesTo("C.Inner");
  }

  public void testInferWithBounds1 () {
    PsiReferenceExpression ref = configure();
    JavaResolveResult resolveResult = ref.advancedResolve(false);
    PsiSubstitutor substitutor = resolveResult.getSubstitutor();
    PsiMethod method = (PsiMethod)resolveResult.getElement();
    PsiType type = substitutor.substitute(method.getTypeParameters()[0]);
    assertEquals("java.lang.String", type.getCanonicalText());
  }

  private PsiReferenceExpression configure() {
    return (PsiReferenceExpression)findReferenceAtCaret("inference/" + getTestName(false) + ".java");
  }

  public void testInferInParamsOnly () {
    checkResolvesTo("C.I");
  }

  public void testInferRawType () {
    checkResolvesTo(CommonClassNames.JAVA_LANG_OBJECT);
  }

  private void checkResolvesTo(@NonNls String typeName) {
    PsiReferenceExpression ref = configure();
    PsiType type = ref.getType();
    assertNotNull(type);
    assertEquals(typeName, type.getCanonicalText());
  }

  public void testInferInSuperAssignment () {
    checkResolvesTo("B<java.lang.String>");
  }

  public void testInferWithWildcards () {
    checkResolvesTo("Collections.SelfComparable");
  }

  public void testInferWithWildcards1 () {
    checkResolvesTo("java.lang.String");
  }

  public void testInferWithWildcards2 () {
    checkResolvesTo("Collection<BarImpl>");
  }

  public void testInferWithWildcards3 () {
    checkResolvesTo("X.Y<java.lang.Long>");
  }

  public void testInferWithWildcards4 () {
    checkResolvesTo("X.Y<java.lang.Long>");
  }

  public void testInferWithWildcards5 () {
    checkResolvesTo("X.Y<java.lang.Long>");
  }

  public void testInferInReturn () {
    checkResolvesTo("T");
  }

  public void testInferAutoboxed () {
    checkResolvesTo("java.lang.Integer");
  }

  public void testInferWithVarargs1 () {
    checkResolvesTo("C2");
  }

  public void testInferWithVarargs2 () {
    checkResolvesTo("C1");
  }

  public void testInferWithVarargs3 () {
    checkResolvesTo("List<int[]>");
  }

  public void testInferWithVarargs4 () {
    checkResolvesTo("List<int[]>");
  }

  public void testInferWithVarargs5 () {
    checkResolvesTo("List<java.lang.Integer>");
  }

  public void testInferWithVarargs6 () {
    checkResolvesTo("List<java.lang.Integer[]>");
  }

  public void testInferPrimitiveArray () {
    checkResolvesTo("double[]");
  }

  public void testSCR41031 () {
    checkResolvesTo("List<T>");
  }

  public void testInferUnchecked () {
    checkResolvesTo(CommonClassNames.JAVA_LANG_OBJECT);
  }

  public void testInferNotNull () {
    checkResolvesTo("E");
  }

  public void testBoundComposition() {
    checkResolvesTo("java.lang.Class<? super ? extends java.lang.Object>");
  }
}
