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
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.annotations.NonNls;

public class OverloadResolutionTest extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/overloadResolution";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTool(new UnusedDeclarationInspection());
  }

  public void testPertinentToApplicabilityOfExplicitlyTypedLambda() throws Exception {
    doTest();
  }

  public void testVoidValueCompatibilityOfImplicitlyTypedLambda() throws Exception {
    doTest();
  }
  
  public void testVoidValueCompatibilityCachedControlFlow() throws Exception {
    doTest();
  }

  public void testVoidValueCompatibilityCanCompleteNormallyWithCallWithExceptionAsLastStatement() throws Exception {
    doTest();
  }

  public void testVoidValueCompatibilityCantCompleteNormallyWithCallWithExceptionAsLastReturnStatement() throws Exception {
    doTest();
  }

  public void testTryCatchWithoutFinallyBlockProcessing() throws Exception {
    doTest(false);
  }

  public void testValueCompatibleWithThrowsStatement() throws Exception {
    doTest(false);
  }

  public void testIDEA102800() throws Exception {
    doTest();
  }

  public void testReturnStatementsInsideNestedLambdasDuringVoidValueCompatibilityChecks() throws Exception {
    doTest();
  }

  public void testIgnoreNonFunctionalArgumentsWhenCheckIfFunctionalMoreSpecific() throws Exception {
    doTest();
  }

  public void testLambdaIsNotCongruentWithFunctionalTypeWithTypeParams() throws Exception {
    doTest();
  }

  public void testDetectPolyExpressionInReturnsOfExplicitlyTypedLambdaWhenPrimitiveCouldWin() throws Exception {
    doTest();
  }

  public void testDetectNotEqualParametersInFunctionalTypesForExactMethodReferences() throws Exception {
    doTest();
  }

  public void testPreferDefaultMethodsOverStatic() throws Exception {
    doTest();
  }

  public void testDefaultAbstractConflictResolution() throws Exception {
    doTest();
  }

  public void testLambdaValueCompatibleWithNestedTryWithResources() throws Exception {
    doTest(false);
  }

  public void testManyOverloadsWithVarargs() throws Exception {
    PlatformTestUtil.startPerformanceTest("Overload resolution with 14 overloads", 10000, () -> doTest(false)).useLegacyScaling().assertTiming();
  }

  public void testConstructorOverloadsWithDiamonds() throws Exception {
    PlatformTestUtil.startPerformanceTest("Overload resolution with chain constructor calls with diamonds", 5000, () -> doTest(false)).useLegacyScaling().assertTiming();
  }

  public void testMultipleOverloadsWithNestedGeneric() throws Exception {
    doTest(false);
  }

  public void testSecondSearchPossibleForFunctionalInterfacesWithPrimitiveFisrtParameter() throws Exception {
    doTest(false);
  }

  public void testIDEA139875() throws Exception {
    doTest();
  }

  public void testMethodReferenceWithTypeArgs() throws Exception {
    doTest();
  }

  public void testPrimitiveVarargsAreNoMoreSpecificThanNonPrimitiveWhenNoArgIsActuallyProvided() throws Exception {
    doTest();
  }

  public void testProperUnrelatedFunctionalInterfacesTypesComparison() throws Exception {
    doTest();
  }

  public void testDoNotCheckConstantIfsDuringValueCompatibleChecks() throws Exception {
    doTest();
  }

  public void testFunctionalExpressionTypeErasure() throws Exception {
    doTest();
  }

  public void testOverrideObjectMethods() throws Exception {
    doTest();
  }

  public void testStaticImportOfObjectsToString() throws Exception {
    doTest();
  }

  public void testConflictsWithRawQualifier() throws Exception {
    doTest();
  }

  public void testIgnoreCandidatesWithLowerApplicabilityLevel() throws Exception {
    doTest();
  }

  public void testSiteSubstituteTypeParameterBoundsWhenCheckForMostSpecific() throws Exception {
    doTest();
  }

  public void testChooseAbstractMethodArbitrarily() throws Exception {
    doTest();
  }

  public void testFunctionalInterfaceIncompatibilityBasedOnAbsenceOfVoidToTypeConvertion() throws Exception {
    doTest();
  }

  public void testNoBoxingWithNullType() throws Exception {
    doTest();
  }

  public void testFunctionalInterfacesAtVarargsPositionMostSpecificCheck() throws Exception {
    doTest();
  }

  public void testIgnoreNumberOfParametersInPotentiallyCompatibleCheckNotToExcludeAllConflicts() throws Exception {
    doTest(false);
  }

  public void testPotentialCompatibilityInCaseWhenNoMethodHasValidNumberOfParameters() throws Exception {
    doTest(false);
  }

  public void testNoNeedToPreferGenericToRawSubstitution() throws Exception {
    doTest();
  }

  public void testLongerParamsWhenVarargs() throws Exception {
    doTest();
  }

  public void testPotentiallyCompatibleShouldCheckAgainstSubstitutedWithSiteSubstitutor() throws Exception {
    doTest(false);
  }

  public void testCompareFormalParametersWithNotionOfSiteSubstitutorInIsMoreSpecificCheck() throws Exception {
    doTest(true);
  }

  public void testDonotIncludeAdditionalConstraintsDuringApplicabilityChecksInsideOverloadResolution() throws Exception {
    doTest(true);
  }

  public void testPreserveErrorsFromOuterVariables() throws Exception {
    doTest(true);
  }

  public void testIDEA151823() throws Exception {
    doTest();
  }

  public void testTypeCalculationOfQualifierShouldNotDependOnOverloadResolutionOfContainingMethodCall() throws Exception {
    doTest();
  }

  public void testIDEA153076() throws Exception {
    doTest();
  }

  //java 8 error
  public void testNotPotentiallyCompatibleMethodReference() throws Exception {
    doTest();
  }

  public void testSpecificFunctionalInterfaces() throws Exception {
    doTest();
  }

  public void testIgnoreStaticCorrectnessDuringOverloadResolution() throws Exception {
    doTest(false);
  }

  public void testIgnoreLambdaVoidValueIncompatibilitiesPreferringMethodWithFunctionalTypeToNonFunctionalType() throws Exception {
    doTest(false);
  }

  public void testVarargComponentTypesShouldBeExcludedFromBoxingComparison() throws Exception {
    doTest(false);
  }

  public void testOverriddenVarargWithaArray() throws Exception {
    doTest();
  }

  public void testForceCleanupErrorsInConditionalWhenBothBranchesProduceError() throws Exception {
    doTest();
  }

  public void testStaticMethodInOuterClassConflictWithToString() throws Exception {
    doTest();
  }

  public void testPreserveStaticMethodConflictsWhenMethodsAreNotHidden() throws Exception {
    doTest(false);
  }

  public void testDontSkipInapplicableMethodsDuringSameSignatureCheck() throws Exception {
    doTest(false);
  }

  public void testInferenceErrorInArgumentWhenWrongOverloadWasChosen() throws Exception {
    doTest(false);
  }

  public void testAdaptReturnTypesOfSiblingMethods() { doTest(false);}

  private void doTest() {
    doTest(true);
  }

  private void doTest(boolean warnings) {
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, getModule(), getTestRootDisposable());
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", warnings, false);
  }
}
