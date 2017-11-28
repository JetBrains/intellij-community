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

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInsight.daemon.impl.analysis.LambdaHighlightingUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public class FunctionalInterfaceTest extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/functionalInterface";

  private void doTestFunctionalInterface(@Nullable String expectedErrorMessage) {
    String filePath = BASE_PATH + "/" + getTestName(false) + ".java";
    configureByFile(filePath);
    final PsiClass psiClass = getJavaFacade().findClass("Foo", GlobalSearchScope.projectScope(getProject()));
    assertNotNull("Class Foo not found", psiClass);

    final String errorMessage = LambdaHighlightingUtil.checkInterfaceFunctional(getJavaFacade().getElementFactory().createType(psiClass));
    assertEquals(expectedErrorMessage, errorMessage);
  }

  public void testSimple() {
    doTestFunctionalInterface(null);
  }

  public void testNoMethods() {
    doTestFunctionalInterface("No target method found");
  }

  public void testMultipleMethods() {
    doTestFunctionalInterface(null);
  }
  
  public void testMultipleMethodsInOne() {
    doTestFunctionalInterface(null);
  }

  public void testIntersectionOf2FunctionalTypesWithEqualSignatures() {
    doTestIntersection(null);
  }

  public void testIntersectionOf2FunctionalTypesWithEqualAfterSubstitutionSignatures() {
    doTestIntersection("Multiple non-overriding abstract methods found in X & Y<Integer>");
  }

  public void testClone() {
    doTestFunctionalInterface("Multiple non-overriding abstract methods found in interface Foo");
  }

  public void testTwoMethodsSameSignature() {
    doTestFunctionalInterface(null);
  } 
  
  public void testTwoMethodsSubSignature() {
    doTestFunctionalInterface(null);
  }
  
  public void testTwoMethodsNoSubSignature() {
    doTestFunctionalInterface("Multiple non-overriding abstract methods found in interface Foo");
  }
  
  public void testTwoMethodsNoSubSignature1() {
    doTestFunctionalInterface("Multiple non-overriding abstract methods found in interface Foo");
  } 
  
  public void testTwoMethodsSameSubstSignature() {
    doTestFunctionalInterface(null);
  }
  
  public void testMethodWithTypeParam() {
    doTestFunctionalInterface("Target method is generic");
  }
  
  public void testTwoMethodsSameSignatureTypeParams() {
    doTestFunctionalInterface("Target method is generic");
  }

  public void testAbstractClass() {
    doTestFunctionalInterface("Target type of a lambda conversion must be an interface");
  }

  public void testIntersectionTypeWithSameBaseInterfaceInConjuncts() {
    doTestIntersection(null);
  }

  private void doTestIntersection(final String expectedMessage) {
    String filePath = BASE_PATH + "/" + getTestName(false) + ".java";
    configureByFile(filePath);
    final PsiTypeCastExpression castExpression =
      PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiTypeCastExpression.class);
    assertNotNull(castExpression);
    final PsiTypeElement castTypeElement = castExpression.getCastType();
    assertNotNull(castTypeElement);
    final PsiType type = castTypeElement.getType();
    final String errorMessage = LambdaHighlightingUtil.checkInterfaceFunctional(type);
    assertEquals(expectedMessage, errorMessage);
  }
}
