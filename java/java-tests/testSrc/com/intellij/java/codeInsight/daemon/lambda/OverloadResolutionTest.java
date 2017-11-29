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

  public void testPertinentToApplicabilityOfExplicitlyTypedLambda() {
    doTest();
  }

  public void testVoidValueCompatibilityOfImplicitlyTypedLambda() {
    doTest();
  }
  
  public void testVoidValueCompatibilityCachedControlFlow() {
    doTest();
  }

  public void testVoidValueCompatibilityCanCompleteNormallyWithCallWithExceptionAsLastStatement() {
    doTest();
  }

  public void testVoidValueCompatibilityCantCompleteNormallyWithCallWithExceptionAsLastReturnStatement() {
    doTest();
  }

  public void testTryCatchWithoutFinallyBlockProcessing() {
    doTest(false);
  }

  public void testValueCompatibleWithThrowsStatement() {
    doTest(false);
  }

  public void testIDEA102800() {
    doTest();
  }

  public void testReturnStatementsInsideNestedLambdasDuringVoidValueCompatibilityChecks() {
    doTest();
  }

  public void testIgnoreNonFunctionalArgumentsWhenCheckIfFunctionalMoreSpecific() {
    doTest();
  }

  public void testLambdaIsNotCongruentWithFunctionalTypeWithTypeParams() {
    doTest();
  }

  public void testDetectPolyExpressionInReturnsOfExplicitlyTypedLambdaWhenPrimitiveCouldWin() {
    doTest();
  }

  public void testDetectNotEqualParametersInFunctionalTypesForExactMethodReferences() {
    doTest();
  }

  public void testPreferDefaultMethodsOverStatic() {
    doTest();
  }

  public void testDefaultAbstractConflictResolution() {
    doTest();
  }

  public void testLambdaValueCompatibleWithNestedTryWithResources() {
    doTest(false);
  }

  public void testManyOverloadsWithVarargs() {
    PlatformTestUtil.startPerformanceTest("Overload resolution with 14 overloads", 10000, () -> doTest(false)).useLegacyScaling().assertTiming();
  }

  public void testConstructorOverloadsWithDiamonds() {
    PlatformTestUtil.startPerformanceTest("Overload resolution with chain constructor calls with diamonds", 5000, () -> doTest(false)).useLegacyScaling().assertTiming();
  }

  public void testMultipleOverloadsWithNestedGeneric() {
    doTest(false);
  }

  public void testSecondSearchPossibleForFunctionalInterfacesWithPrimitiveFisrtParameter() {
    doTest(false);
  }

  public void testIDEA139875() {
    doTest();
  }

  public void testMethodReferenceWithTypeArgs() {
    doTest();
  }

  public void testPrimitiveVarargsAreNoMoreSpecificThanNonPrimitiveWhenNoArgIsActuallyProvided() {
    doTest();
  }

  public void testProperUnrelatedFunctionalInterfacesTypesComparison() {
    doTest();
  }

  public void testDoNotCheckConstantIfsDuringValueCompatibleChecks() {
    doTest();
  }

  public void testFunctionalExpressionTypeErasure() {
    doTest();
  }

  public void testOverrideObjectMethods() {
    doTest();
  }

  public void testStaticImportOfObjectsToString() {
    doTest();
  }

  public void testConflictsWithRawQualifier() {
    doTest();
  }

  public void testIgnoreCandidatesWithLowerApplicabilityLevel() {
    doTest();
  }

  public void testSiteSubstituteTypeParameterBoundsWhenCheckForMostSpecific() {
    doTest();
  }

  public void testChooseAbstractMethodArbitrarily() {
    doTest();
  }

  public void testFunctionalInterfaceIncompatibilityBasedOnAbsenceOfVoidToTypeConvertion() {
    doTest();
  }

  public void testNoBoxingWithNullType() {
    doTest();
  }

  public void testFunctionalInterfacesAtVarargsPositionMostSpecificCheck() {
    doTest();
  }

  public void testIgnoreNumberOfParametersInPotentiallyCompatibleCheckNotToExcludeAllConflicts() {
    doTest(false);
  }

  public void testPotentialCompatibilityInCaseWhenNoMethodHasValidNumberOfParameters() {
    doTest(false);
  }

  public void testNoNeedToPreferGenericToRawSubstitution() {
    doTest();
  }

  public void testLongerParamsWhenVarargs() {
    doTest();
  }

  public void testPotentiallyCompatibleShouldCheckAgainstSubstitutedWithSiteSubstitutor() {
    doTest(false);
  }

  public void testCompareFormalParametersWithNotionOfSiteSubstitutorInIsMoreSpecificCheck() {
    doTest(true);
  }

  public void testDonotIncludeAdditionalConstraintsDuringApplicabilityChecksInsideOverloadResolution() {
    doTest(true);
  }

  public void testPreserveErrorsFromOuterVariables() {
    doTest(true);
  }

  public void testIDEA151823() {
    doTest();
  }

  public void testTypeCalculationOfQualifierShouldNotDependOnOverloadResolutionOfContainingMethodCall() {
    doTest();
  }

  public void testIDEA153076() {
    doTest();
  }

  //java 8 error
  public void testNotPotentiallyCompatibleMethodReference() {
    doTest();
  }

  public void testSpecificFunctionalInterfaces() {
    doTest();
  }

  public void testIgnoreStaticCorrectnessDuringOverloadResolution() {
    doTest(false);
  }

  public void testIgnoreLambdaVoidValueIncompatibilitiesPreferringMethodWithFunctionalTypeToNonFunctionalType() {
    doTest(false);
  }

  public void testVarargComponentTypesShouldBeExcludedFromBoxingComparison() {
    doTest(false);
  }

  public void testOverriddenVarargWithaArray() {
    doTest();
  }

  public void testForceCleanupErrorsInConditionalWhenBothBranchesProduceError() {
    doTest();
  }

  public void testStaticMethodInOuterClassConflictWithToString() {
    doTest();
  }

  public void testPreserveStaticMethodConflictsWhenMethodsAreNotHidden() {
    doTest(false);
  }

  public void testDontSkipInapplicableMethodsDuringSameSignatureCheck() {
    doTest(false);
  }

  public void testInferenceErrorInArgumentWhenWrongOverloadWasChosen() {
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
