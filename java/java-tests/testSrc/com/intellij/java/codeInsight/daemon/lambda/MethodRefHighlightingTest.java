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

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class MethodRefHighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/methodRef/";

  @BeforeEach
  void setUp() {
    daemonFixture.get().getFixture().enableInspections(new UnusedDeclarationInspection());
  }

  @Test public void testValidContext() { doTest(); }
  @Test public void testAssignability() { doTest(); }
  @Test public void testAmbiguity() { doTest(); }
  @Test public void testMethodReferenceReceiver() { doTest(); }
  @Test public void testMethodRefMisc() { doTest(); }
  @Test public void testMethodTypeParamsInference() { doTest(); }
  @Test public void testMethodRefMisc1() { doTest(); }
  @Test public void testQualifierTypeArgs() { doTest(); }
  @Test public void testStaticProblems() { doTest(); }
  @Test public void testConstructorRefs() { doTest(); }
  @Test public void testConstructorRefsInnerClasses() { doTest(); }
  @Test public void testVarargs() { doTest(); }
  @Test public void testVarargs1() { doTest(); }
  @Test public void testVarargs2() { doTest(); }
  @Test public void testConstructorRefInnerFromSuper() { doTest(); }
  @Test public void testReferenceParameters() { doTest(); }
  @Test public void testRawQualifier() { doTest(); }
  @Test public void testCyclicInference() { doTest(); }
  @Test public void testAccessModifiers() { doTest(); }
  @Test public void testDefaultConstructor() { doTest(); }
  @Test public void testWildcards() { doTest(); }
  @Test public void testVarargsInReceiverPosition() { doTest(); }
  @Test public void testInferenceFromMethodReference() { doTest(); }
  @Test public void testAssignability1() { doTest(); }
  @Test public void testTypeArgumentsOnMethodRefs() { doTest(); }
  @Test public void testConstructorAssignability() { doTest(); }
  @Test public void testConstructorWithoutParams() { doTest(); }
  @Test public void testSOE() { doTest(); }
  @Test public void testInferenceFromReturnType() { doTest(true); }
  @Test public void testReturnTypeSpecific() { doTest(true); }
  @Test public void testResolveConflicts() { doTest(true); }
  @Test public void testIntersectionTypeInCast() { doTest(false); }
  @Test public void testUnhandledExceptions() { doTest(); }
  @Test public void testCapturedWildcards() { doTest(); }
  @Test public void testConstructorNonAbstractAbstractExpected() { doTest(); }
  @Test public void test100441() { doTest(); }
  @Test public void testSuperClassSubst() { doTest(); }
  @Test public void testNonParameterizedReceiver() { doTest(); }
  @Test public void testFunctionalInterfaceMethodIsGeneric() { doTest(); /*accepted for method ref, though forbidden for lambda*/ }
  @Test public void testStaticMethodOnTypedClassReference() { doTest(); }
  @Test public void testRefOnArrayDeclaration() { doTest(); }
  @Test public void testGetClassSpecifics() { doTest(); }
  @Test public void testAbstractMethod() { doTest(); }
  @Test public void testMethodRefAcceptance() { doTest(); }
  @Test public void testVarargsMethodRef() { doTest(); }
  @Test public void testExprReceiver() { doTest(); }
  @Test public void testVoidReturnTypeAmbiguity() { doTest(true); }
  @Test public void testTypeParameterWithExtendsList() { doTest(); }
  @Test public void testIDEA112323() { doTest(); }
  @Test public void testExactReferencesToArrayCreation() { doTest(); }
  @Test public void testUnknownQualifierClass() { doTest(); }
  @Test public void testQualifiersInStaticContext() { doTest(); }
  @Test public void testInvalidFunctionalTypeInReturnStmt() { doTest(); }
  @Test public void testIDEA127765() { doTest(); }
  @Test public void testIntersectionTypesInReceiverPosition() { doTest(); }

  private void doTest() {
    doTest(false);
  }

  private void doTest(boolean warnings) {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, myFixture.getModule(), myFixture.getTestRootDisposable());
    daemonFixture.get().doTest(BASE_PATH + myFixture.getTestName(false) + ".java", warnings, false);
  }
}