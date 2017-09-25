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

public class NewLambdaHighlightingTest extends LightDaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/newLambda/";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTool(new UnusedDeclarationInspection());
  }

  public void testIDEA93586() { doTest(); }
  public void testIDEA113573() { doTest(); }
  public void testIDEA112922() { doTest(); }
  public void testIDEA113504() { doTest(); }
  public void testAfterAbstractPipeline2() { doTest(); }
  public void testIDEA116252() { doTest(); }
  public void testIDEA106670() { doTest(); }
  public void testIDEA116548() { doTest(); }
  public void testOverloadResolutionSAM() { doTest(); }
  public void testIntersectionTypesDuringInference() { doTest(); }
  public void testIncludeConstraintsWhenParentMethodIsDuringCalculation() { doTest(); }
  public void testUseCalculatedSubstitutor() { doTest(); }
  public void testArgumentOfAnonymousClass() { doTest(); }
  public void testEllipsis() { doTest(); }
  public void testOuterMethodPropagation() { doTest(); }
  public void testRecursiveCalls() { doTest(); }
  public void testGroundTargetTypeForImplicitLambdas() { doTest(); }
  public void testAdditionalConstraintsReduceOrder() { doTest(); }
  public void testAdditionalConstraintSubstitution() { doTest(); }
  public void testFunctionalInterfacesCalculation() { doTest(); }
  public void testMissedSiteSubstitutorDuringDeepAdditionalConstraintsGathering() { doTest(); }
  public void testIDEA120992() { doTest(); }
  public void testTargetTypeConflictResolverShouldNotTryToEvaluateCurrentArgumentType() { doTest(); }
  public void testIDEA119535() { doTest(); }
  public void testIDEA119003() { doTest(); }
  public void testIDEA125674() { doTest(); }
  public void testIDEA117124() { doTest(); }
  public void testWildcardParameterization() { doTest(); }
  public void testDiamondInLambdaReturn() { doTest(); }
  public void testIDEA118965() { doTest(); }
  public void testIDEA121315() { doTest(); }
  public void testIDEA118965comment() { doTest(); }
  public void testIDEA122074() { doTest(); }
  public void testIDEA122084() { doTest(); }
  public void testAdditionalConstraintDependsOnNonMentionedVars() { doTest(); }
  public void testIDEA122616() { doTest(); }
  public void testIDEA122700() { doTest(); }
  public void testIDEA122406() { doTest(); }
  public void testNestedCallsInsideLambdaReturnExpression() { doTest(); }
  public void testIDEA123731() { doTest(); }
  public void testIDEA123869() { doTest(); }
  public void testIDEA123848() { doTest(); }
  public void testOnlyLambdaAtTypeParameterPlace() { doTest(); }
  public void testLiftedIntersectionType() { doTest(); }
  public void testInferenceFromReturnStatements() { doTest(); }
  public void testDownUpThroughLambdaReturnStatements() { doTest(); }
  public void testIDEA124547() { doTest(); }
  public void testIDEA118362() { doTest(); }
  public void testIDEA126056() { doTest(); }
  public void testIDEA125254() { doTest(); }
  public void testIDEA124961() { doTest(); }
  public void testIDEA124961_1_8_0_40() { doTest(); }
  public void testIDEA126109() { doTest(); }
  public void testIDEA126809() { doTest(); }
  public void testIDEA124424() { doTest(); }
  public void testNestedLambdaExpressions1() { doTest(); }
  public void testNestedLambdaExpressionsNoFormalParams() { doTest(); }
  public void testNestedLambdaExpressionsNoFormalParams1() { doTest(); }
  public void testDeepNestedLambdaExpressionsNoFormalParams() { doTest(); }
  public void testNestedLambdaExpressionsNoFormalParamsStopAtStandalone() { doTest(); }
  public void testNestedLambdaCheckedExceptionsConstraints() { doTest(); }
  public void testNestedLambdaWithInferenceVariableAsTargetType() { doTest(); }
  public void testIDEA127596() { doTest(); }
  public void testIDEA124983() { doTest(); }
  public void testIDEA123951() { doTest(); }
  public void testIDEA124190() { doTest(); }
  public void testIDEA127124comment() { doTest(); }
  public void testParenthesizedExpressionsDuringConstrainsCollection() { doTest(); }
  public void testIDEA126778() { doTest(); }
  public void testEnsureGroundTypeToGetLambdaParameterType() { doTest(); }
  public void testSameParametrizationCheckTakesTypeParametersIntoAccount() { doTest(); }
  public void testCheckedExceptionsConstraintsSubstitutions() { doTest(); }
  public void testCheckedExceptionsConstraintsSubstitutions1() { doTest(); }
  public void testCheckedExceptionsConstraintsSubstitutions2() { doTest(); }
  public void testCheckedExceptionsConstraintsSubstitutionsDeepInBody() { doTest(); }
  public void testIDEA130129() { doTest(); }
  public void testIDEA130920() { doTest(); }
  public void testExpectedReturnTypeInAnonymousInsideLambda() { doTest(); }
  public void testIDEA131700() { doTest(); }
  public void testPropertiesInsteadOfSiteSubstitutorIfAny() { doTest(); }
  public void testIDEA127124() { doTest(); }
  public void testIDEA123987() { doTest(); }
  public void testIDEA136759() { doTest(); }
  public void testInfiniteLoopAndValueCompatibility() { doTest(); }
  public void testAcceptInferredVariablesBeforeAdditionalConstraintsLeadToFail() { doTest(); }
  public void testEnsureNoCaptureIsPerformedOverTargetTypeOfCastExpressionWhichMarksFunctionalExpression() { doTest(); }
  public void testCaptureInReturnStatementOfLambdaExpression() { doTest(); }
  public void testControlFlowAnalysisFailedValueCompatibilityUnchanged() { doTest(); }
  public void testNonAccessibleFunctionalInterfaceTypeArguments() { doTest(); }
  public void testIntersectionTypeOfDifferentParameterizationOfTheSameClassInNonWildcardParameterization() { doTest(); /* JDK-8043374 */ }
  public void testPreserveCapturedWildcardsDuringNonWildcardParameterization() { doTest(); }
  public void testIDEA136401() { doTest(); }
  public void testIDEA133920() { doTest(); }
  public void testIDEA132253() { doTest(); }
  public void testIDEA144840() { doTest(); }
  public void testRecursiveAtSiteSubstitutorsWithAdditionalConstraints() { doTest(); }
  public void testIDEA136325() { doTest(); }
  public void testIDEA127215() { doTest(); }
  public void testGroundTargetTypeForExpectedReturnTypeOfLambdaExpression() { doTest(); }
  public void testIDEA149224() { doTest(); }
  public void testIDEA149670() { doTest(); }
  public void testIDEA149709() { doTest(); }
  public void testResolveOrderShouldTakeIntoAccountDependenciesOfAlreadyResolvedVars() { doTest(); }
  public void testCodeBlockLambdaWithIsValueCompatibleChecks() { doTest(); }
  public void testCodeBlockLambdaWithoutParamsIsValueCompatibleChecks() { doTest(); }
  public void testInferenceFromReturnLambdaStatementWhereContainingMethodNonGeneric() { doTest(); }
  public void testCLikeArrayDeclarationInLambdaWithExplicitTypes() { doTest(); }
  public void testAdditionalConstraintsOrderWhenOutputVariablesAlreadyHaveProperEqualBound() { doTest(); }
  public void testIDEA153284() { doTest(); }
  public void testAvoidPartlyRawTypesAsTheyWontBeTreatedAsWildcardParameterizedAnyway() { doTest(); }
  public void testDeepChainOfNestedLambdasOverCachedTopLevel() { doTest(); }
  public void testIDEA153999() { doTest(); }
  public void testFieldReferencedFromLambdaInitializations() { doTest(); }
  public void testRawSiteSubstitutorWithExpectedGenericsParameterType() { doTest(); }
  public void testPreserveCapturedWildcardAsLambdaParameterType() { doTest(); }
  public void testDontWarnAboutNestedLambdaForAProblemInOuter() { doTest(); }
  public void testUncheckedPartial() { doTest(); }
  public void testVarargMethodWithThrownTypes() { doTest(); }
  public void testExceptionInLambdaBodyCheck() { doTest(); }
  public void testHighlightFaultyLambdaReturnExpression() { doTest(); }
  public void testUncheckedConstraintOnInferenceVariableWithProperUpperBound() { doTest(); }
  public void testCollectLambdaAdditionalConstraintsByGroundType() { doTest(); }
  public void testInferTypeParametersFromFunctionalInterfaceInputs() { doTest(); }
  public void testGroundTargetTypeWhenAbstractMethodInSuperclass() { doTest(); }
  public void testNestedLambdasWithInferenceOfReturnTypeInTheLatestLambda() { doTest(); }
  public void testCapturedWildcardNotOpenedDuringInference() { doTest(); }
  public void testIgnoreStandaloneExpressionsInLambdaReturnForNestedCalls() { doTest(); }
  public void testArrayNotAFunctionalInterface() { doTest(); }
  public void testRawSubstitutionForInterfaceMethod() { doTest(); }
  public void testConditionalExpressionInLambdaReturns() { doTest(); }
  public void testLambdaWithFormalTypeParameters() { doTest(); }
  public void testIDEA174924() { doTest(); }
  public void testVoidValueCompatibilityWithBreakInSwitch() { doTest(); }
  public void testExceptionInferenceForVarargMethods() { doTest(); }

  private void doTest() {
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, getModule(), getTestRootDisposable());
    doTest(BASE_PATH + getTestName(false) + ".java", false, false);
  }
}