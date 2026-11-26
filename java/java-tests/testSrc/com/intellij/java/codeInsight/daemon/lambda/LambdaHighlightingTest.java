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
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class LambdaHighlightingTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.JDK_22));
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/highlighting/";

  @BeforeEach
  void setUp() {
    daemonFixture.get().getFixture().enableInspections(new UnusedDeclarationInspection());
  }

  @Test public void testStaticAccess() { doTest(); }
  @Test public void testEffectiveFinal() { doTest(); }
  @Test public void testFieldInitializedUsedInLambda() { doTest(); }
  @Test public void testReassignUsedVars() { doTest(); }
  @Test public void testLambdaContext() { doTest(); }
  @Test public void testReturnTypeCompatibility() { doTest(); }

  @Test public void testTypeArgsConsistency() { doTest(); }
  @Test public void testTypeArgsConsistencyMisc1() { doTest(); }
  @Test public void testTypeArgsConsistencyMisc2() { doTest(); }
  @Test public void testTypeArgsConsistencyWithoutParams() { doTest(); }
  @Test public void testIncompatibleReturnTypes() { doTest(); }
  @Test public void testWildcardBounds() { doTest(); }
  @Test public void testInferenceOnMethodCallSite() { doTest(); }
  @Test public void testInferFromTypeArgs() { doTest(); }
  @Test public void testAmbiguity1() { doTest(); }
  //public void testAmbiguity2() { doTest(); }
  @Test public void testAmbiguityVarargs() { doTest(); }
  @Test public void testAmbiguityRawGenerics() { doTest(); }
  @Test public void testDefaultMethod() { doTest(); }
  @Test public void testLambdaOnVarargsPlace() { doTest(); }
  @Test public void testLambdaRawOrNot() { doTest(); }  //todo incorrect testdata
  @Test public void testReturnTypeCompatibility1() { doTest(); }
  @Test public void testNoInferenceResult() { doTest(); }
  @Test public void testInferenceFromArgs() { doTest(); }
  @Test public void testInContexts() { doTest(); }
  @Test public void testCastInContexts() { doTest(); }
  @Test public void testUnhandledException() { doTest(); }
  @Test public void testConditionalExpr() { doTest(); }
  @Test public void testIncompleteSubst() { doTest(); }
  @Test public void testVariableInitialization() { doTest(); }
  @Test public void testUnreachableStatement() { doTest(); }
  @Test public void testUnhandledExceptions() { doTest(); }
  @Test public void testReturnValue() { doTest(); }
  @Test public void testAlreadyUsedParamName() { doTest(); }
  @Test public void testRecursiveAccess() { doTest(); }
  @Test public void testIncompatibleFormalParameterTypes() { doTest(); }
  @Test public void testNestedLambdas() { doTest(); }
  @Test public void testEnumConstants() { doTest(); }
  @Test public void testRawWhenNoParams() { doTest(); }
  @Test public void testUseIncompleteParentSubstitutor() { doTest(); }
  @Test public void testReturnTypeCompatibilityBeforeSpecificsCheck() { doTest(); }
  @Test public void testIntersectionTypeInCast() { doTest(); }
  @Test public void testAmbiguitySpecificReturn() { doTest(true); }
  @Test public void testFunctionalInterfaceAnnotation() { doTest(); }
  @Test public void testFunctionalInterfaceAnnotation2() { doTest(); }
  @Test public void testFunctionalInterfaceAnnotation3() { doTest(); }
  @Test public void testAmbiguityReturnValueResolution() { doTest(); }
  @Test public void testAmbiguityReturnValueResolution1() { doTest(); }
  @Test public void testAmbiguityReturnValueResolution2() { doTest(true); }
  @Test public void testAmbiguityReturnValueResolution3() { doTest(); }
  @Test public void testLambdaOnVarargsPlace1() { doTest(); }
  @Test public void testInferenceFromSecondLambda() { doTest(); }
  @Test public void testAcceptRawSubstForLambda() { doTest(); }
  @Test public void testCheckFunctionalInterfaceAccess() { doTest(); }
  @Test public void testVoidCompatibility() { doTest(); }
  @Test public void testConditionalInferenceFromOppositePart() { doTest(); }
  @Test public void testDeclaredTypeParameterBoundsAndUnboundedWildcard() { doTest(); }
  @Test public void testConflictResolution() { doTest(); }
  @Test public void testIDEA108195() { doTest(); }
  @Test public void testDiamondInference() { doTest(); }
  @Test public void testFunctionalInterfaceCheck() { doTest(); }
  @Test public void testUnderscores() { daemonFixture.get().setLanguageLevel(LanguageLevel.JDK_1_8); doTest(true); }
  @Test public void testReturnTypeAmbiguity() { doTest(); }
  @Test public void testWildcardsAndFormalLambdaParams() { doTest(); }
  @Test public void testFinalInitializer() { doTest(); }
  @Test public void testBreakContinueInside() { doTest(); }
  @Test public void testSameLambdaParamNames() { doTest(); }
  @Test public void testIDEA123308() { doTest(); }
  @Test public void testIntersection() { doTest(); }
  @Test public void testNoBoxingInLambdaFormalParams() { doTest(); }
  @Test public void testGenericNotGenericInterfaceMethod() { doTest(); }
  @Test public void testInferredFromCast() { doTest(); }
  @Test public void testReferencedFromSelf() { doTest(); }
  @Test public void testFriendlyMessageInBadInference() { doTest(); }

  private void doTest() {
    doTest(false);
  }

  private void doTest(boolean checkWarnings) {
    daemonFixture.get().doTest(BASE_PATH + daemonFixture.get().getFixture().getTestName(false) + ".java", checkWarnings, false);
  }
}