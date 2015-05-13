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
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NonNls;

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
