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
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class GraphInferenceHighlightingTest extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/graphInference";

  public void testNestedCalls() throws Exception {
    doTest();
  }

  public void testNestedCallsSameMethod() throws Exception {
    doTest();
  }

  public void testChainedInference() throws Exception {
    doTest();
  }

  public void testChainedInference1() throws Exception {
    doTest();
  }

  public void testReturnStmt() throws Exception {
    doTest();
  }

  public void testInferenceFromSiblings() throws Exception {
    doTest();
  }

  public void testChainedInferenceTypeParamsOrderIndependent() throws Exception {
    doTest();
  }

  public void testCyclicParamsDependency() throws Exception {
    doTest();
  }

  public void testInferenceForFirstArg() throws Exception {
    doTest();
  }

  public void testConditionalExpressionsInference() throws Exception {
    doTest();
  }

  public void testInferenceFromTypeParamsBounds() throws Exception {
    doTest();
  }

  public void testInferenceFromNotEqualTypeParamsBounds() throws Exception {
    doTest();
  }

  public void testSOEDuringInferenceFromParamBounds() throws Exception {
    doTest();
  }

  public void testDiamondsUsedToDetectArgumentType() throws Exception {
    doTest();
  }

  public void testInferFromTypeArgs() throws Exception {
    doTest();
  }

  public void testDefaultConstructorAsArgument() throws Exception {
    doTest();
  }

  public void testAfterAbstractPipeline() throws Exception {
    doTest();
  }

  public void testCapturedReturnTypes() throws Exception {
    doTest();
  }

  public void testClsCapturedReturnTypes() throws Exception {
    doTest();
  }

  public void testOverloadChooserOfReturnType() throws Exception {
    doTest();
  }

  public void testIDEA98866() throws Exception {
    doTest();
  }

  public void testIncompleteSubstitution() throws Exception {
    doTest();
  }

  public void testJDK8028774() throws Exception {
    doTest();
  }

  public void testErasedByReturnConstraint() throws Exception {
    doTest();
  }

  public void testIDEA104429() throws Exception {
    doTest();
  }

  public void testTargetTypeByOverloadedMethod() throws Exception {
    doTest();
  }

  public void testTargetTypeByOverloadedMethod2() throws Exception {
    doTest();
  }

  public void testGrandParentTypeParams() throws Exception {
    doTest();
  }

  public void testDeepCallsChain() throws Exception {
    doTest();
  }

  public void testArrayPassedToVarargsMethod() throws Exception {
    doTest();
  }

  public void testIDEA121055() throws Exception {
    doTest();
  }

  public void testTargetTypeByAnonymousClass() throws Exception {
    doTest();
  }

  public void testStaticInheritorsAmbiguity() throws Exception {
    doTest();
  }

  public void testNestedCalls1() throws Exception {
    doTest();
  }

  public void testMostSpecificVarargsCase() throws Exception {
    doTest();
  }

  public void testLiftedCaptureToOuterCall() throws Exception {
    doTest();
  }

  public void testSiteSubstitutionForReturnConstraint() throws Exception {
    doTest();
  }

  public void testSiteSubstitutionInExpressionConstraints() throws Exception {
    doTest();
  }

  public void testIncorporationWithEqualsBoundsSubstitution() throws Exception {
    doTest();
  }

  public void testOuterCallConflictResolution() throws Exception {
    doTest();
  }

  public void testVarargsOnNonPertinentPlace() throws Exception {
    doTest();
  }

  public void testRawTypeFromParent() throws Exception {
    doTest();
  }

  public void testRawTypeFromParentArrayType() throws Exception {
    doTest();
  }

  public void testInferFromConditionalExpressionCondition() throws Exception {
    doTest();
  }

  public void testPrimitiveWrapperConditionInReturnConstraint() throws Exception {
    doTest();
  }

  public void testIDEA128174() throws Exception {
    doTest();
  }

  public void testIDEA128101() throws Exception {
    doTest();
  }

  public void testOuterCallOverloads() throws Exception {
    doTest();
  }

  public void testIDEA127928() throws Exception {
    doTest();
  }
  public void testIDEA128766() throws Exception {
    doTest();
  }

  public void testSameMethodNestedChainedCallsNearFunctionInterfaces() throws Exception {
    doTest();
  }

  public void testInfiniteTypes() throws Exception {
    doTest();
  }

  public void testIDEA126163() throws Exception {
    doTest();
  }

  public void testIncompatibleBoundsFromAssignment() throws Exception {
    doTest();
  }

  public void testFreshVariablesCreatedDuringResolveDependingOnAlreadyResolvedVariables() throws Exception {
    doTest();
  }

  public void testCallToGenericMethodsOfNonGenericClassInsideRawInheritor() throws Exception {
    doTest();
  }

  public void testIDEA130549() throws Exception {
    doTest();
  }

  public void testIDEA130547() throws Exception {
    doTest();
  }

  public void testUncheckedConversionWithRecursiveTypeParams() throws Exception {
    doTest();
  }

  public void testIDEA132725() throws Exception {
    doTest();
  }

  public void testIDEA134277() throws Exception {
    doTest();
  }

  public void testSameMethodCalledWithDifferentArgsResultingInDependenciesBetweenSameTypeParams() throws Exception {
    doTest();
  }

  public void testNestedMethodCallsWithVarargs() throws Exception {
    doTest();
  }

  public void testDisjunctionTypeEquality() throws Exception {
    doTest();
  }

  public void testNestedConditionalExpressions() throws Exception {
    doTest();
  }

  public void testOuterMethodCallOnRawType() throws Exception {
    doTest();
  }

  public void testIDEA143390() throws Exception {
    doTest();
  }

  public void testIntersectionWithArray() throws Exception {
    doTest();
  }

  public void testIncorporationWithCaptureCalcGlbToGetOneTypeParameterBound() throws Exception {
    doTest();
  }

  public void testEnumConstantInference() throws Exception {
    doTest();
  }

  public void testReturnConstraintsWithCaptureIncorporationOfFreshVariables() throws Exception {
    doTest();
  }

  public void testArrayTypeAssignability() throws Exception {
    doTest();
  }

  public void testAcceptFirstPairOfCommonSupertypesDuringUpUpIncorporation() throws Exception {
    doTest();
  }

  public void testDiamondWithExactMethodReferenceInside() throws Exception {
    doTest();
  }

  public void testRecursiveCallsWithNestedInference() throws Exception {
    doTest();
  }

  public void testIncorporationWithRawSubstitutors() throws Exception {
    doTest();
  }

  public void testIncorporationOfBoundsAsTypeArguments() throws Exception {
    doTest();
  }

  public void testEliminateIntersectionTypeWildcardElimination() throws Exception {
    doTest();
  }

  public void testDoNotIgnoreConflictingUpperBounds() throws Exception {
    doTest();
  }

  public void testCapturedWildcardWithArrayTypeBound() throws Exception {
    doTest();
  }

  public void testPolyConditionalExpressionWithTargetPrimitive() throws Exception {
    doTest();
  }

  public void testCaptureConstraint() throws Exception {
    doTest();
  }

  public void testPullUncheckedWarningNotionThroughNestedCalls() throws Exception {
    doTest();
  }

  public void testIDEA149774() throws Exception {
    doTest();
  }

  public void testDisjunctionTypes() throws Exception {
    doTest();
  }

  public void testPushErasedStateToArguments() throws Exception {
    doTest();
  }

  public void testStopAtStandaloneConditional() throws Exception {
    doTest();
  }

  public void testTransitiveInferenceVariableDependencies() throws Exception {
    doTest();
  }

  public void testInferenceVariablesErasure() throws Exception {
    doTest();
  }

  public void testUncheckedWarningConvertingToInferenceVariable() throws Exception {
    doTest();
  }

  public void testUncheckedWarningDuringStrictSubtyping() throws Exception {
    doTest();
  }

  public void testIDEA150688() throws Exception {
    doTest();
  }

  public void testGlbValidityWithCapturedWildcards() throws Exception {
    doTest();
  }

  public void testCapturedVariablesAcceptance() throws Exception {
    doTest();
  }

  public void testVariableNamesOfNestedCalls() throws Exception {
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, getModule(), getTestRootDisposable());
    String filePath = BASE_PATH + "/" + getTestName(false) + ".java";
    configureByFile(filePath);
    Collection<HighlightInfo> infos = doHighlighting();

    List<String> tooltips = new ArrayList<>();

    for (HighlightInfo info : infos) {
      if (info.getSeverity() == HighlightSeverity.ERROR) {
        tooltips.add(info.getToolTip());
      }
    }

    assertTrue(tooltips.contains("<html><body><table border=0><tr><td>" +
                                     "<b>identity(&nbsp;)&nbsp;</b></td><td colspan=1>in <b>Function</b>&nbsp;cannot be applied</td></tr><tr><td>to</td><td><b>()</b>&nbsp;" +
                                     
                           "</td></tr></table><br/>" +
                           "reason: no instance(s) of type variable(s) K, U exist so that Map&lt;K, U&gt; conforms to Function&lt;U, V&gt;" +
                           "</body></html>"));
    assertTrue(tooltips.contains(
                           "<html><body><table border=0><tr><td>" +
                                    "<b>identity(&nbsp;)&nbsp;</b></td><td colspan=1>in <b>Function</b>&nbsp;cannot be applied</td></tr><tr><td>to</td><td><b>()</b>&nbsp;" +
                           "</td></tr></table><br/>" +
                           "reason: no instance(s) of type variable(s) K, U exist so that Map&lt;K, U&gt; conforms to Function&lt;U, V&gt;" +
                           "</body></html>"));
  }

  private void doTest() throws Exception {
    doTest(false);
  }

  private void doTest(final boolean checkWarnings) throws Exception {
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, getModule(), getTestRootDisposable());
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", checkWarnings, false);
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk18();
  }
}
