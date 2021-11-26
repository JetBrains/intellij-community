// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

class NewLambdaHighlightingTest extends LightJavaCodeInsightFixtureTestCase5 {
  @Test void testIDEA93586() { doTest(); }
  @Test void testIDEA113573() { doTest(); }
  @Test void testIDEA112922() { doTest(); }
  @Test void testIDEA113504() { doTest(); }
  @Test void testAfterAbstractPipeline2() { doTest(); }
  @Test void testIDEA116252() { doTest(); }
  @Test void testIDEA106670() { doTest(); }
  @Test void testIDEA116548() { doTest(); }
  @Test void testOverloadResolutionSAM() { doTest(); }
  @Test void testIntersectionTypesDuringInference() { doTest(); }
  @Test void testIncludeConstraintsWhenParentMethodIsDuringCalculation() { doTest(); }
  @Test void testUseCalculatedSubstitutor() { doTest(); }
  @Test void testArgumentOfAnonymousClass() { doTest(); }
  @Test void testEllipsis() { doTest(); }
  @Test void testOuterMethodPropagation() { doTest(); }
  @Test void testRecursiveCalls() { doTest(); }
  @Test void testGroundTargetTypeForImplicitLambdas() { doTest(); }
  @Test void testAdditionalConstraintsReduceOrder() { doTest(); }
  @Test void testAdditionalConstraintSubstitution() { doTest(); }
  @Test void testFunctionalInterfacesCalculation() { doTest(); }
  @Test void testMissedSiteSubstitutorDuringDeepAdditionalConstraintsGathering() { doTest(); }
  @Test void testIDEA120992() { doTest(); }
  @Test void testTargetTypeConflictResolverShouldNotTryToEvaluateCurrentArgumentType() { doTest(); }
  @Test void testIDEA119535() { doTest(); }
  @Test void testIDEA119003() { doTest(); }
  @Test void testIDEA125674() { doTest(); }
  @Test void testIDEA117124() { doTest(); }
  @Test void testWildcardParameterization() { doTest(); }
  @Test void testDiamondInLambdaReturn() { doTest(); }
  @Test void testIDEA118965() { doTest(); }
  @Test void testIDEA121315() { doTest(); }
  @Test void testIDEA118965comment() { doTest(); }
  @Test void testIDEA122074() { doTest(); }
  @Test void testIDEA122084() { doTest(); }
  @Test void testAdditionalConstraintDependsOnNonMentionedVars() { doTest(); }
  @Test void testIDEA122616() { doTest(); }
  @Test void testIDEA122700() { doTest(); }
  @Test void testIDEA122406() { doTest(); }
  @Test void testNestedCallsInsideLambdaReturnExpression() { doTest(); }
  @Test void testIDEA123731() { doTest(); }
  @Test void testIDEA123869() { doTest(); }
  @Test void testIDEA123848() { doTest(); }
  @Test void testOnlyLambdaAtTypeParameterPlace() { doTest(); }
  @Test void testLiftedIntersectionType() { doTest(); }
  @Test void testInferenceFromReturnStatements() { doTest(); }
  @Test void testDownUpThroughLambdaReturnStatements() { doTest(); }
  @Test void testIDEA124547() { doTest(); }
  @Test void testIDEA118362() { doTest(); }
  @Test void testIDEA126056() { doTest(); }
  @Test void testIDEA125254() { doTest(); }
  @Test void testIDEA124961() { doTest(); }
  @Test void testIDEA124961_1_8_0_40() { doTest(); }
  @Test void testIDEA126109() { doTest(); }
  @Test void testIDEA126809() { doTest(); }
  @Test void testIDEA124424() { doTest(); }
  @Test void testNestedLambdaExpressions1() { doTest(); }
  @Test void testNestedLambdaExpressionsNoFormalParams() { doTest(); }
  @Test void testNestedLambdaExpressionsNoFormalParams1() { doTest(); }
  @Test void testDeepNestedLambdaExpressionsNoFormalParams() { doTest(); }
  @Test void testNestedLambdaExpressionsNoFormalParamsStopAtStandalone() { doTest(); }
  @Test void testNestedLambdaCheckedExceptionsConstraints() { doTest(); }
  @Test void testNestedLambdaWithInferenceVariableAsTargetType() { doTest(); }
  @Test void testIDEA127596() { doTest(); }
  @Test void testIDEA124983() { doTest(); }
  @Test void testIDEA123951() { doTest(); }
  @Test void testIDEA124190() { doTest(); }
  @Test void testIDEA127124comment() { doTest(); }
  @Test void testParenthesizedExpressionsDuringConstrainsCollection() { doTest(); }
  @Test void testIDEA126778() { doTest(); }
  @Test void testEnsureGroundTypeToGetLambdaParameterType() { doTest(); }
  @Test void testSameParametrizationCheckTakesTypeParametersIntoAccount() { doTest(); }
  @Test void testCheckedExceptionsConstraintsSubstitutions() { doTest(); }
  @Test void testCheckedExceptionsConstraintsSubstitutions1() { doTest(); }
  @Test void testCheckedExceptionsConstraintsSubstitutions2() { doTest(); }
  @Test void testCheckedExceptionsConstraintsSubstitutionsDeepInBody() { doTest(); }
  @Test void testIDEA130129() { doTest(); }
  @Test void testIDEA130920() { doTest(); }
  @Test void testExpectedReturnTypeInAnonymousInsideLambda() { doTest(); }
  @Test void testIDEA131700() { doTest(); }
  @Test void testPropertiesInsteadOfSiteSubstitutorIfAny() { doTest(); }
  @Test void testIDEA127124() { doTest(); }
  @Test void testIDEA123987() { doTest(); }
  @Test void testIDEA136759() { doTest(); }
  @Test void testInfiniteLoopAndValueCompatibility() { doTest(); }
  @Test void testAcceptInferredVariablesBeforeAdditionalConstraintsLeadToFail() { doTest(); }
  @Test void testEnsureNoCaptureIsPerformedOverTargetTypeOfCastExpressionWhichMarksFunctionalExpression() { doTest(); }
  @Test void testCaptureInReturnStatementOfLambdaExpression() { doTest(); }
  @Test void testControlFlowAnalysisFailedValueCompatibilityUnchanged() { doTest(); }
  @Test void testNonAccessibleFunctionalInterfaceTypeArguments() { doTest(); }
  @Test void testIntersectionTypeOfDifferentParameterizationOfTheSameClassInNonWildcardParameterization() { doTest(); /* JDK-8043374 */ }
  @Test void testPreserveCapturedWildcardsDuringNonWildcardParameterization() { doTest(); }
  @Test void testIDEA136401() { doTest(); }
  @Test void testIDEA133920() { doTest(); }
  @Test void testIDEA132253() { doTest(); }
  @Test void testIDEA144840() { doTest(); }
  @Test void testRecursiveAtSiteSubstitutorsWithAdditionalConstraints() { doTest(); }
  @Test void testIDEA136325() { doTest(); }
  @Test void testIDEA127215() { doTest(); }
  @Test void testGroundTargetTypeForExpectedReturnTypeOfLambdaExpression() { doTest(); }
  @Test void testIDEA149224() { doTest(); }
  @Test void testIDEA149670() { doTest(); }
  @Test void testIDEA149709() { doTest(); }
  @Test void testResolveOrderShouldTakeIntoAccountDependenciesOfAlreadyResolvedVars() { doTest(); }
  @Test void testCodeBlockLambdaWithIsValueCompatibleChecks() { doTest(); }
  @Test void testCodeBlockLambdaWithoutParamsIsValueCompatibleChecks() { doTest(); }
  @Test void testInferenceFromReturnLambdaStatementWhereContainingMethodNonGeneric() { doTest(); }
  @Test void testCLikeArrayDeclarationInLambdaWithExplicitTypes() { doTest(); }
  @Test void testAdditionalConstraintsOrderWhenOutputVariablesAlreadyHaveProperEqualBound() { doTest(); }
  @Test void testIDEA153284() { doTest(); }
  @Test void testAvoidPartlyRawTypesAsTheyWontBeTreatedAsWildcardParameterizedAnyway() { doTest(); }
  @Test void testDeepChainOfNestedLambdasOverCachedTopLevel() { doTest(); }
  @Test void testIDEA153999() { doTest(); }
  @Test void testFieldReferencedFromLambdaInitializations() { doTest(); }
  @Test void testRawSiteSubstitutorWithExpectedGenericsParameterType() { doTest(); }
  @Test void testPreserveCapturedWildcardAsLambdaParameterType() { doTest(); }
  @Test void testDontWarnAboutNestedLambdaForAProblemInOuter() { doTest(); }
  @Test void testUncheckedPartial() { doTest(); }
  @Test void testVarargMethodWithThrownTypes() { doTest(); }
  @Test void testExceptionInLambdaBodyCheck() { doTest(); }
  @Test void testHighlightFaultyLambdaReturnExpression() { doTest(); }
  @Test void testUncheckedConstraintOnInferenceVariableWithProperUpperBound() { doTest(); }
  @Test void testCollectLambdaAdditionalConstraintsByGroundType() { doTest(); }
  @Test void testInferTypeParametersFromFunctionalInterfaceInputs() { doTest(); }
  @Test void testGroundTargetTypeWhenAbstractMethodInSuperclass() { doTest(); }
  @Test void testNestedLambdasWithInferenceOfReturnTypeInTheLatestLambda() { doTest(); }
  @Test void testCapturedWildcardNotOpenedDuringInference() { doTest(); }
  @Test void testIgnoreStandaloneExpressionsInLambdaReturnForNestedCalls() { doTest(); }
  @Test void testArrayNotAFunctionalInterface() { doTest(); }
  @Test void testRawSubstitutionForInterfaceMethod() { doTest(); }
  @Test void testConditionalExpressionInLambdaReturns() { doTest(); }
  @Test void testLambdaWithFormalTypeParameters() { doTest(); }
  @Test void testIDEA174924() { doTest(); }
  @Test void testVoidValueCompatibilityWithBreakInSwitch() { doTest(); }
  @Test void testLambdaChainWithCapturedWildcards() { doTest(); }
  @Test void testExceptionInferenceForVarargMethods() { doTest(); }
  @Test void testConditionalBooleanAsFunctionalInterfaceType() { doTest(); }
  @Test void testUnhandledExceptionInLambdaChain() { doTest(); }
  @Test void testFunctionalBound() { doTest(); }
  @Test void testValidFixesOnUnresolvedMethod() { doTest(); }
  @Test void testPolyExpressionInVoidCompatibleLambdaReturn() { doTest(); }
  @Test void testStopAtTypeCastWhenSearchForTopMostNode() { doTest(); }
  @Test void testLambdaWithExplicitTypeAndTargetTypeParameter() { doTest(); }
  @Test void testAmbiguousConstructorCallWithLambdaInside() { doTest(); }

  @Override
  protected @NotNull String getRelativePath() {
    return super.getRelativePath() + "/codeInsight/daemonCodeAnalyzer/lambda/newLambda/";
  }

  private void doTest() {
    getFixture().testHighlighting(false, false, false, getTestName(false) + ".java");
  }
}