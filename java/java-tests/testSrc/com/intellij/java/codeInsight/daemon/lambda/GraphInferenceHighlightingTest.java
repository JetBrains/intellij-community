// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.idea.TestFor;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaDescriptorOfTestName;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class GraphInferenceHighlightingTest {
  private static final LightProjectDescriptor DESCRIPTOR = lightJavaTestDescriptor();
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(DESCRIPTOR);
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture =
    lightDaemonAnalyzerFixture(projectFixture, lightJavaDescriptorOfTestName(DESCRIPTOR));

  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/graphInference/";

  @Test public void testNestedCalls() { doTest(); }
  @Test public void testNestedCallsSameMethod() { doTest(); }
  @Test public void testChainedInference() { doTest(); }
  @Test public void testChainedInference1() { doTest(); }
  @Test public void testReturnStmt() { doTest(); }
  @Test public void testInferenceFromSiblings() { doTest(); }
  @Test public void testChainedInferenceTypeParamsOrderIndependent() { doTest(); }
  @Test public void testCyclicParamsDependency() { doTest(); }
  @Test public void testInferenceForFirstArg() { doTest(); }
  @Test public void testConditionalExpressionsInference() { doTest(); }
  @Test public void testInferenceFromTypeParamsBounds() { doTest(); }
  @Test public void testInferenceFromNotEqualTypeParamsBounds() { doTest(); }
  @Test public void testSOEDuringInferenceFromParamBounds() { doTest(); }
  @Test public void testDiamondsUsedToDetectArgumentType() { doTest(); }
  @Test public void testInferFromTypeArgs() { doTest(); }
  @Test public void testDefaultConstructorAsArgument() { doTest(); }
  @Test public void testAfterAbstractPipeline() { doTest(); }
  @Test public void testCapturedReturnTypes() { doTest(); }
  @Test public void testClsCapturedReturnTypes() { doTest(); }
  @Test public void testOverloadChooserOfReturnType() { doTest(); }
  @Test public void testIDEA98866() { doTest(); }
  @Test public void testDiamondWithConditionalInside() { doTest(); }
  @Test public void testIncompleteSubstitution() { doTest(); }
  @Test public void testJDK8028774() { doTest(); }
  @Test public void testErasedByReturnConstraint() { doTest(); }
  @Test public void testIDEA104429() { doTest(); }
  @Test public void testTargetTypeByOverloadedMethod() { doTest(); }
  @Test public void testTargetTypeByOverloadedMethod2() { doTest(); }
  @Test public void testGrandParentTypeParams() { doTest(); }
  @Test public void testDeepCallsChain() { doTest(); }
  @Test public void testArrayPassedToVarargsMethod() { doTest(); }
  @Test public void testWrongNumberOfParametersWithLambdaOnUnexpectedPlace() { doTest(); }
  @Test public void testIDEA121055() { doTest(); }
  @Test public void testTargetTypeByAnonymousClass() { doTest(); }
  @Test public void testStaticInheritorsAmbiguity() { doTest(); }
  @Test public void testNestedCalls1() { doTest(); }
  @Test public void testMostSpecificVarargsCase() { doTest(); }
  @Test public void testLiftedCaptureToOuterCall() { doTest(); }
  @Test public void testSiteSubstitutionForReturnConstraint() { doTest(); }
  @Test public void testSiteSubstitutionInExpressionConstraints() { doTest(); }
  @Test public void testIncorporationWithEqualsBoundsSubstitution() { doTest(); }
  @Test public void testOuterCallConflictResolution() { doTest(); }
  @Test public void testVarargsOnNonPertinentPlace() { doTest(); }
  @Test public void testRawTypeFromParent() { doTest(); }
  @Test public void testRawTypeFromParentArrayType() { doTest(); }
  @Test public void testInferFromConditionalExpressionCondition() { doTest(); }
  @Test public void testPrimitiveWrapperConditionInReturnConstraint() { doTest(); }
  @Test public void testIDEA128174() { doTest(); }
  @Test public void testIDEA128101() { doTest(); }
  @Test public void testOuterCallOverloads() { doTest(); }
  @Test public void testIDEA127928() { doTest(); }
  @Test public void testIDEA128766() { doTest(); }
  @Test public void testSameMethodNestedChainedCallsNearFunctionInterfaces() { doTest(); }
  @Test public void testInfiniteTypes() { doTest(); }
  @Test public void testIDEA126163() { doTest(); }
  @Test public void testIncompatibleBoundsFromAssignment() { doTest(); }
  @Test public void testFreshVariablesCreatedDuringResolveDependingOnAlreadyResolvedVariables() { doTest(); }
  @Test public void testCallToGenericMethodsOfNonGenericClassInsideRawInheritor() { doTest(); }
  @Test public void testIDEA130549() { doTest(); }
  @Test public void testIDEA130547() { doTest(); }
  @Test public void testUncheckedConversionWithRecursiveTypeParams() { doTest(); }
  @Test public void testIDEA132725() { doTest(); }
  @Test public void testIDEA134277() { doTest(); }
  @Test public void testSameMethodCalledWithDifferentArgsResultingInDependenciesBetweenSameTypeParams() { doTest(); }
  @Test public void testNestedMethodCallsWithVarargs() { doTest(); }
  @Test public void testDisjunctionTypeEquality() { doTest(); }
  @Test public void testNestedConditionalExpressions() { doTest(); }
  @Test public void testOuterMethodCallOnRawType() { doTest(); }
  @Test public void testIDEA143390() { doTest(); }
  @Test public void testIntersectionWithArray() { doTest(); }
  @Test public void testIncorporationWithCaptureCalcGlbToGetOneTypeParameterBound() { doTest(); }
  @Test public void testEnumConstantInference() { doTest(); }
  @Test public void testReturnConstraintsWithCaptureIncorporationOfFreshVariables() { doTest(); }
  @Test public void testArrayTypeAssignability() { doTest(); }
  @Test public void testAcceptFirstPairOfCommonSupertypesDuringUpUpIncorporation() { doTest(); }
  @Test public void testDiamondWithExactMethodReferenceInside() { doTest(); }
  @Test public void testRecursiveCallsWithNestedInference() { doTest(); }
  @Test public void testIncorporationWithRawSubstitutors() { doTest(); }
  @Test public void testIncorporationOfBoundsAsTypeArguments() { doTest(); }
  @Test public void testEliminateIntersectionTypeWildcardElimination() { doTest(); }
  @Test public void testDoNotIgnoreConflictingUpperBounds() { doTest(); }
  @Test public void testCapturedWildcardWithArrayTypeBound() { doTest(); }
  @Test public void testPolyConditionalExpressionWithTargetPrimitive() { doTest(); }
  @Test public void testCaptureConstraint() { doTest(); }
  @Test public void testPullUncheckedWarningNotionThroughNestedCalls() { doTest(); }
  @Test public void testIDEA149774() { doTest(); }
  @Test public void testDisjunctionTypes() { doTest(); }
  @Test public void testValidIntersectionTypeWithCapturedBounds() { doTest(); }
  @Test public void testValidIntersectionTypeWithCapturedBounds1() { doTest(); }
  @Test public void testPushErasedStateToArguments() { doTest(); }
  @Test public void testStopAtStandaloneConditional() { doTest(); }
  @Test public void testTransitiveInferenceVariableDependencies() { doTest(); }
  @Test public void testInferenceVariablesErasure() { doTest(); }
  @Test public void testUncheckedWarningConvertingToInferenceVariable() { doTest(); }
  @Test public void testUncheckedWarningDuringStrictSubtyping() { doTest(); }
  @Test public void testIDEA150688() { doTest(); }
  @Test public void testGlbValidityWithCapturedWildcards() { doTest(); }
  @Test public void testCapturedVariablesAcceptance() { doTest(); }
  @Test public void testPullingErrorMessagesFromSubExpressionsToTheTopLevel() { doTest(); }
  @Test public void testHighlightArgumentWithProblem() { doTest(); }
  @Test public void testIDEA153632() { doTest(); }

  @Test
  public void testPartialRawSubstitutionToAvoidInferringObjectsWhenRawExpected() {
    daemonFixture.get().getFixture().enableInspections(new UncheckedWarningLocalInspection());
    doTest(true);
  }

  @Test public void testIDEA154278() { doTest(); }
  @Test public void testPrimitiveTypeInReturnConstraintWithUncheckedConversion() { doTest(); }
  @Test public void testPolyMethodCallOnLeftSideOfAssignment() { doTest(); }
  @Test public void testTreatConditionalExpressionAsPolyIfNewExpressionWithDiamondsIsUsed() { doTest(); }

  @Test
  public void testVariableNamesOfNestedCalls() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, myFixture.getModule(), myFixture.getTestRootDisposable());
    String filePath = BASE_PATH + myFixture.getTestName(false) + ".java";
    daemonFixture.get().configureByFile(filePath);
    Collection<HighlightInfo> infos = daemonFixture.get().doHighlighting();

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

  @Test public void testNameConflictBetweenFreshVariables() { doTest(); }
  @Test public void testOrderOfGenericsParameterizations() { doTest(); }
  @Test public void testLowerBoundAssignability() { doTest(); }
  @Test public void testCreateFreshVariablesOnlyForWildcardPlacesDuringReturnTypeProcessing() { doTest(); }
  @Test public void testCapturedConversionDuringDirectSuperCheck() { doTest(); }
  //public void _testResolutionOrderForVariableCycles() { doTest(); }
  @Test public void testPostponeUnresolvedVariables() { doTest(); }
  @Test public void testErasureOfReturnTypeIffUncheckedConversionWasNecessaryDuringApplicabilityCheckOnly() { doTest(); }
  @Test public void testErasureOfReturnTypeIffUncheckedConversionWasNecessaryDuringApplicabilityCheckOnly1() { doTest(); }
  @Test public void testErasureOfReturnTypeIffUncheckedConversionWasNecessaryDuringApplicabilityCheckOnly2() { doTest(); }
  @Test public void testTwoDifferentParameterizationCheckWithInterfaceTypeArguments() { doTest(); }
  @Test public void testNonGenericInnerClassOfGenericsOuterInReturnType() { doTest(); }
  @Test public void testInferredApplicabilityForAnonymousClassConstructor() { doTest(); }
  @Test public void testNonGenericInnerClassOfGenericsOuterWithWildcardsInReturnType() { doTest(); }
  @Test public void testUncheckedConversionDuringProperTypeExpressionConstraintResolution() { doTest(); }
  //public void _testAssignabilityOfStandaloneExpressionsDuringApplicabilityCheck() { doTest(); }
  @Test public void testRecursiveTypeWithCapture() { doTest(); }
  @Test public void testFreshVariablesDuringApplicabilityCheck() { doTest(); }
  @Test public void testPostponeConditionalExpressionErrorToMethodLevel() { doTest(); }
  @Test public void testNestedCallsWithFreshVariables() { doTest(); }

  @Test public void testPertinentToApplicabilityCheckForBlockLambda() { doTest(); }
  @Test public void testCheckGlbConflictsAfterIntersectionTypeCreated() { doTest(); }
  @Test public void testRestoreCapturedWildcardsInReturnTypesWhenNoAdditionalConstraintsDetected() { doTest(); }
  @Test public void testIntersectionWithSameInterfaceButDifferentTypeArgument() { doTest(); }
  @Test public void testVarargsMethodPreferred() { doTest(); }
  @Test public void testSameNamedFreshVariables() { doTest(); }
  @Test public void testEnsureOrderOfFreshVariablesWhenCaptureNonProperTypes() { doTest(); }
  @Test public void testFreshVariablesBounds() { doTest(); }
  @Test public void testRawInIntersection() { doTest(); }

  @Test
  public void testApplicabilityCheckFailsExpressionTypeCheckPasses() {
    doTest();
  }
  @Test public void testNotInferredVarShouldNotBeUsedForInferenceJava10() { doTest(); }
  @Test public void testJavac8UnCaptureBug() { doTest(); }
  @Test public void testLambdaInConditional() { doTest(); }
  @Test public void testTopLevelParentNoParameters() { doTest(); }
  @Test public void testRawTypeOnTheRight() { doTest(); }
  @Test public void testMutualBoundPromotion() { doTest(); }
  @Test public void testIntersectionWithCapturedSuperWildcard() { doTest(); }
  @TestFor(issues = "IDEA-153641")
  @Test public void testCapturedWildcardAsStandaloneArgument() { doTest(); }
  @Test public void testComparatorInsideDiamondInsideLambda() { doTest(); }
  @Test public void testCapturedExtendsWildcardWithProperLowerBound() { doTest(); }
  @TestFor(issues = "IDEA-396230")
  @Test public void testCaptureOfWildcardParameterizedLowerBound() { doTest(); }

  private void doTest() {
    doTest(false);
  }

  private void doTest(final boolean checkWarnings) {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, myFixture.getModule(), myFixture.getTestRootDisposable());
    daemonFixture.get().doTest(BASE_PATH + myFixture.getTestName(false) + ".java", checkWarnings, false);
  }
}