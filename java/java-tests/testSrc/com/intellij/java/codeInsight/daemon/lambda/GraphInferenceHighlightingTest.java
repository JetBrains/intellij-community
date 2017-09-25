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
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.IdeaTestUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class GraphInferenceHighlightingTest extends LightDaemonAnalyzerTestCase {
  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/graphInference/";

  public void testNestedCalls() { doTest(); }
  public void testNestedCallsSameMethod() { doTest(); }
  public void testChainedInference() { doTest(); }
  public void testChainedInference1() { doTest(); }
  public void testReturnStmt() { doTest(); }
  public void testInferenceFromSiblings() { doTest(); }
  public void testChainedInferenceTypeParamsOrderIndependent() { doTest(); }
  public void testCyclicParamsDependency() { doTest(); }
  public void testInferenceForFirstArg() { doTest(); }
  public void testConditionalExpressionsInference() { doTest(); }
  public void testInferenceFromTypeParamsBounds() { doTest(); }
  public void testInferenceFromNotEqualTypeParamsBounds() { doTest(); }
  public void testSOEDuringInferenceFromParamBounds() { doTest(); }
  public void testDiamondsUsedToDetectArgumentType() { doTest(); }
  public void testInferFromTypeArgs() { doTest(); }
  public void testDefaultConstructorAsArgument() { doTest(); }
  public void testAfterAbstractPipeline() { doTest(); }
  public void testCapturedReturnTypes() { doTest(); }
  public void testClsCapturedReturnTypes() { doTest(); }
  public void testOverloadChooserOfReturnType() { doTest(); }
  public void testIDEA98866() { doTest(); }
  public void testIncompleteSubstitution() { doTest(); }
  public void testJDK8028774() { doTest(); }
  public void testErasedByReturnConstraint() { doTest(); }
  public void testIDEA104429() { doTest(); }
  public void testTargetTypeByOverloadedMethod() { doTest(); }
  public void testTargetTypeByOverloadedMethod2() { doTest(); }
  public void testGrandParentTypeParams() { doTest(); }
  public void testDeepCallsChain() { doTest(); }
  public void testArrayPassedToVarargsMethod() { doTest(); }
  public void testIDEA121055() { doTest(); }
  public void testTargetTypeByAnonymousClass() { doTest(); }
  public void testStaticInheritorsAmbiguity() { doTest(); }
  public void testNestedCalls1() { doTest(); }
  public void testMostSpecificVarargsCase() { doTest(); }
  public void testLiftedCaptureToOuterCall() { doTest(); }
  public void testSiteSubstitutionForReturnConstraint() { doTest(); }
  public void testSiteSubstitutionInExpressionConstraints() { doTest(); }
  public void testIncorporationWithEqualsBoundsSubstitution() { doTest(); }
  public void testOuterCallConflictResolution() { doTest(); }
  public void testVarargsOnNonPertinentPlace() { doTest(); }
  public void testRawTypeFromParent() { doTest(); }
  public void testRawTypeFromParentArrayType() { doTest(); }
  public void testInferFromConditionalExpressionCondition() { doTest(); }
  public void testPrimitiveWrapperConditionInReturnConstraint() { doTest(); }
  public void testIDEA128174() { doTest(); }
  public void testIDEA128101() { doTest(); }
  public void testOuterCallOverloads() { doTest(); }
  public void testIDEA127928() { doTest(); }
  public void testIDEA128766() { doTest(); }
  public void testSameMethodNestedChainedCallsNearFunctionInterfaces() { doTest(); }
  public void testInfiniteTypes() { doTest(); }
  public void testIDEA126163() { doTest(); }
  public void testIncompatibleBoundsFromAssignment() { doTest(); }
  public void testFreshVariablesCreatedDuringResolveDependingOnAlreadyResolvedVariables() { doTest(); }
  public void testCallToGenericMethodsOfNonGenericClassInsideRawInheritor() { doTest(); }
  public void testIDEA130549() { doTest(); }
  public void testIDEA130547() { doTest(); }
  public void testUncheckedConversionWithRecursiveTypeParams() { doTest(); }
  public void testIDEA132725() { doTest(); }
  public void testIDEA134277() { doTest(); }
  public void testSameMethodCalledWithDifferentArgsResultingInDependenciesBetweenSameTypeParams() { doTest(); }
  public void testNestedMethodCallsWithVarargs() { doTest(); }
  public void testDisjunctionTypeEquality() { doTest(); }
  public void testNestedConditionalExpressions() { doTest(); }
  public void testOuterMethodCallOnRawType() { doTest(); }
  public void testIDEA143390() { doTest(); }
  public void testIntersectionWithArray() { doTest(); }
  public void testIncorporationWithCaptureCalcGlbToGetOneTypeParameterBound() { doTest(); }
  public void testEnumConstantInference() { doTest(); }
  public void testReturnConstraintsWithCaptureIncorporationOfFreshVariables() { doTest(); }
  public void testArrayTypeAssignability() { doTest(); }
  public void testAcceptFirstPairOfCommonSupertypesDuringUpUpIncorporation() { doTest(); }
  public void testDiamondWithExactMethodReferenceInside() { doTest(); }
  public void testRecursiveCallsWithNestedInference() { doTest(); }
  public void testIncorporationWithRawSubstitutors() { doTest(); }
  public void testIncorporationOfBoundsAsTypeArguments() { doTest(); }
  public void testEliminateIntersectionTypeWildcardElimination() { doTest(); }
  public void testDoNotIgnoreConflictingUpperBounds() { doTest(); }
  public void testCapturedWildcardWithArrayTypeBound() { doTest(); }
  public void testPolyConditionalExpressionWithTargetPrimitive() { doTest(); }
  public void testCaptureConstraint() { doTest(); }
  public void testPullUncheckedWarningNotionThroughNestedCalls() { doTest(); }
  public void testIDEA149774() { doTest(); }
  public void testDisjunctionTypes() { doTest(); }
  public void testValidIntersectionTypeWithCapturedBounds() { doTest(); }
  public void testValidIntersectionTypeWithCapturedBounds1() { doTest(); }
  public void testPushErasedStateToArguments() { doTest(); }
  public void testStopAtStandaloneConditional() { doTest(); }
  public void testTransitiveInferenceVariableDependencies() { doTest(); }
  public void testInferenceVariablesErasure() { doTest(); }
  public void testUncheckedWarningConvertingToInferenceVariable() { doTest(); }
  public void testUncheckedWarningDuringStrictSubtyping() { doTest(); }
  public void testIDEA150688() { doTest(); }
  public void testGlbValidityWithCapturedWildcards() { doTest(); }
  public void testCapturedVariablesAcceptance() { doTest(); }
  public void testPullingErrorMessagesFromSubExpressionsToTheTopLevel() { doTest(); }
  public void testHighlightArgumentWithProblem() { doTest(); }
  public void testIDEA153632() { doTest(); }

  public void testPartialRawSubstitutionToAvoidInferringObjectsWhenRawExpected() {
    enableInspectionTool(new UncheckedWarningLocalInspection());
    doTest(true);
  }

  public void testIDEA154278() { doTest(); }
  public void testPrimitiveTypeInReturnConstraintWithUncheckedConversion() { doTest(); }
  public void testPolyMethodCallOnLeftSideOfAssignment() { doTest(); }
  public void testTreatConditionalExpressionAsPolyIfNewExpressionWithDiamondsIsUsed() { doTest(); }

  public void testVariableNamesOfNestedCalls() {
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, getModule(), getTestRootDisposable());
    String filePath = BASE_PATH + getTestName(false) + ".java";
    configureByFile(filePath);
    Collection<HighlightInfo> infos = doHighlighting();

    List<String> tooltips = new ArrayList<>();
    for (HighlightInfo info : infos) {
      if (info.getSeverity() == HighlightSeverity.ERROR) {
        tooltips.add(info.getToolTip());
      }
    }

    boolean found = false;
    for (String tooltip : tooltips) {
      if (tooltip.contains("no instance(s) of type variable(s) K, U exist so that Map&lt;K, U&gt; conforms to Function&lt;U, V&gt;")) {
        found = true;
        break;
      }
    }

    if (!found) {
      fail(StringUtil.join(tooltips, ", "));
    }
  }

  public void testCreateFreshVariablesOnlyForWildcardPlacesDuringReturnTypeProcessing() { doTest(); }
  public void testCapturedConversionDuringDirectSuperCheck() { doTest(); }
  //public void _testResolutionOrderForVariableCycles() { doTest(); }
  public void testPostponeUnresolvedVariables() { doTest(); }
  public void testErasureOfReturnTypeIffUncheckedConversionWasNecessaryDuringApplicabilityCheckOnly() { doTest(); }
  public void testTwoDifferentParameterizationCheckWithInterfaceTypeArguments() { doTest(); }
  public void testNonGenericInnerClassOfGenericsOuterInReturnType() { doTest(); }
  public void testNonGenericInnerClassOfGenericsOuterWithWildcardsInReturnType() { doTest(); }
  public void testUncheckedConversionDuringProperTypeExpressionConstraintResolution() { doTest(); }
  //public void _testAssignabilityOfStandaloneExpressionsDuringApplicabilityCheck() { doTest(); }
  public void testRecursiveTypeWithCapture() { doTest(); }
  public void testFreshVariablesDuringApplicabilityCheck() { doTest(); }

  public void testPertinentToApplicabilityCheckForBlockLambda() { doTest(); }
  public void testRestoreCapturedWildcardsInReturnTypesWhenNoAdditionalConstraintsDetected() { doTest(); }

  public void testApplicabilityCheckFailsExpressionTypeCheckPasses() {
    doTest();
  }

  public void testTopLevelParentNoParameters() {
    doTest();
  }

  private void doTest() {
    doTest(false);
  }

  private void doTest(final boolean checkWarnings) {
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, getModule(), getTestRootDisposable());
    doTest(BASE_PATH + getTestName(false) + ".java", checkWarnings, false);
  }
}