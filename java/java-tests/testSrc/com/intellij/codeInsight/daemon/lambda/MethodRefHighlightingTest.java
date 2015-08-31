/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NonNls;

public class MethodRefHighlightingTest extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/methodRef";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTool(new UnusedDeclarationInspection());
  }

  public void testValidContext() { doTest(); }
  public void testAssignability() { doTest(); }
  public void testAmbiguity() { doTest(); }
  public void testMethodReferenceReceiver() { doTest(); }
  public void testMethodRefMisc() { doTest(); }
  public void testMethodTypeParamsInference() { doTest(); }
  public void testMethodRefMisc1() { doTest(); }
  public void testQualifierTypeArgs() { doTest(); }
  public void testStaticProblems() { doTest(); }
  public void testConstructorRefs() { doTest(); }
  public void testConstructorRefsInnerClasses() { doTest(); }
  public void testVarargs() { doTest(); }
  public void testVarargs1() { doTest(); }
  public void testVarargs2() { doTest(); }
  public void testConstructorRefInnerFromSuper() { doTest(); }
  public void testReferenceParameters() { doTest(); }
  public void testRawQualifier() { doTest(); }
  public void testCyclicInference() { doTest(); }
  public void testAccessModifiers() { doTest(); }
  public void testDefaultConstructor() { doTest(); }
  public void testWildcards() { doTest(); }
  public void testVarargsInReceiverPosition() { doTest(); }
  public void testInferenceFromMethodReference() { doTest(); }
  public void testAssignability1() { doTest(); }
  public void testTypeArgumentsOnMethodRefs() { doTest(); }
  public void testConstructorAssignability() { doTest(); }
  public void testConstructorWithoutParams() { doTest(); }
  public void testSOE() { doTest(); }
  public void testInferenceFromReturnType() { doTest(true); }
  public void testReturnTypeSpecific() { doTest(true); }
  public void testResolveConflicts() { doTest(true); }
  public void testIntersectionTypeInCast() { doTest(false); }
  public void testUnhandledExceptions() { doTest(); }
  public void testCapturedWildcards() { doTest(); }
  public void testConstructorNonAbstractAbstractExpected() { doTest(); }
  public void test100441() { doTest(); }
  public void testSuperClassSubst() { doTest(); }
  public void testNonParameterizedReceiver() { doTest(); }
  public void testFunctionalInterfaceMethodIsGeneric() { doTest(); /*accepted for method ref, though forbidden for lambda*/ }
  public void testStaticMethodOnTypedClassReference() { doTest(); }
  public void testRefOnArrayDeclaration() { doTest(); }
  public void testGetClassSpecifics() { doTest(); }
  public void testAbstractMethod() { doTest(); }
  public void testMethodRefAcceptance() { doTest(); }
  public void testVarargsMethodRef() { doTest(); }
  public void testExprReceiver() { doTest(); }
  public void testVoidReturnTypeAmbiguity() { doTest(true); }

  public void testTypeParameterWithExtendsList() throws Exception {
    doTest();
  }

  public void testIDEA112323() throws Exception {
    doTest();
  }

  public void testExactReferencesToArrayCreation() {
    doTest();
  }

  public void testUnknownQualifierClass() throws Exception {
    doTest();
  }

  public void testQualifiersInStaticContext() throws Exception {
    doTest();
  }
  public void testInvalidFunctionalTypeInReturnStmt() throws Exception {
    doTest();
  }

  public void testIDEA127765() throws Exception {
    doTest();
  }

  public void testIntersectionTypesInReceiverPosition() throws Exception {
    doTest();
  }

  private void doTest() {
    doTest(false);
  }

  private void doTest(boolean warnings) {
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, getModule(), getTestRootDisposable());
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", warnings, false);
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk18();
  }
}
