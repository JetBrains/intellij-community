/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.lambda;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.psi.PsiClass;
import com.intellij.psi.LambdaUtil;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public class FunctionalInterfaceTest extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/functionalInterface";

  private void doTestFunctionalInterface(@Nullable String expectedErrorMessage) throws Exception {
    String filePath = BASE_PATH + "/" + getTestName(false) + ".java";
    configureByFile(filePath);
    final PsiClass psiClass = getJavaFacade().findClass("Foo", GlobalSearchScope.projectScope(getProject()));
    assertNotNull("Class Foo not found", psiClass);

    final String errorMessage = LambdaUtil.checkInterfaceFunctional(psiClass);
    assertEquals(expectedErrorMessage, errorMessage);
  }

  public void testSimple() throws Exception {
    doTestFunctionalInterface(null);
  }

  public void testNoMethods() throws Exception {
    doTestFunctionalInterface("No target method found");
  }

  public void testMultipleMethods() throws Exception {
    doTestFunctionalInterface(null);
  }
  
  public void testMultipleMethodsInOne() throws Exception {
    doTestFunctionalInterface(null);
  } 

  public void testClone() throws Exception {
    doTestFunctionalInterface("Multiple non-overriding abstract methods found");
  }

  public void testTwoMethodsSameSignature() throws Exception {
    doTestFunctionalInterface(null);
  } 
  
  public void testTwoMethodsSubSignature() throws Exception {
    doTestFunctionalInterface(null);
  }
  
  public void testTwoMethodsNoSubSignature() throws Exception {
    doTestFunctionalInterface("Multiple non-overriding abstract methods found");
  }
  
  public void testTwoMethodsNoSubSignature1() throws Exception {
    doTestFunctionalInterface("Multiple non-overriding abstract methods found");
  } 
  
  public void testTwoMethodsSameSubstSignature() throws Exception {
    doTestFunctionalInterface(null);
  }
  
  public void testMethodWithTypeParam() throws Exception {
    doTestFunctionalInterface(null);
  }
  
  public void testTwoMethodsSameSignatureTypeParams() throws Exception {
    doTestFunctionalInterface(null);
  }

  public void testAbstractClass() throws Exception {
    doTestFunctionalInterface("Target type of a lambda conversion must be an interface");
  }
}
