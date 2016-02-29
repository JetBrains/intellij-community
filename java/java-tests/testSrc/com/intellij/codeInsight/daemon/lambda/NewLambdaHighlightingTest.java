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
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NonNls;

public class NewLambdaHighlightingTest extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/newLambda";

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

  public void testNestedLambdaCheckedExceptionsConstraints() throws Exception {
    doTest();
  }

  public void testNestedLambdaWithInferenceVariableAsTargetType() throws Exception {
    doTest();
  }

  public void testIDEA127596() throws Exception {
    doTest();
  }

  public void testIDEA124983() throws Exception {
    doTest();
  }

  public void testIDEA123951() throws Exception {
    doTest();
  }

  public void testIDEA124190() throws Exception {
    doTest();
  }
  public void testIDEA127124comment() throws Exception {
    doTest();
  }

  public void testParenthesizedExpressionsDuringConstrainsCollection() throws Exception {
    doTest();
  }

  public void testIDEA126778() throws Exception {
    doTest();
  }

  public void testEnsureGroundTypeToGetLambdaParameterType() throws Exception {
    doTest();
  }

  public void testSameParametrizationCheckTakesTypeParametersIntoAccount() throws Exception {
    doTest();
  }

  public void testCheckedExceptionsConstraintsSubstitutions() throws Exception {
    doTest();
  }

  public void testCheckedExceptionsConstraintsSubstitutions1() throws Exception {
    doTest();
  }

  public void testCheckedExceptionsConstraintsSubstitutions2() throws Exception {
    doTest();
  }

  public void testCheckedExceptionsConstraintsSubstitutionsDeepInBody() throws Exception {
    doTest();
  }

  public void testIDEA130129() throws Exception {
    doTest();
  }

  public void testIDEA130920() throws Exception {
    doTest();
  }

  public void testExpectedReturnTypeInAnonymousInsideLambda() throws Exception {
    doTest();
  }

  public void testIDEA131700() throws Exception {
    doTest();
  }

  public void testPropertiesInsteadOfSiteSubstitutorIfAny() throws Exception {
    doTest();
  }

  public void testIDEA127124() throws Exception {
    doTest();
  }

  public void testIDEA123987() throws Exception {
    doTest();
  }

  public void testIDEA136759() throws Exception {
    doTest();
  }

  public void testInfiniteLoopAndValueCompatibility() throws Exception {
    doTest();
  }

  public void testAcceptInferredVariablesBeforeAdditionalConstraintsLeadToFail() throws Exception {
    doTest(false);
  }

  public void testEnsureNoCaptureIsPerformedOverTargetTypeOfCastExpressionWhichMarksFunctionalExpression() throws Exception {
    doTest();
  }

  public void testCaptureInReturnStatementOfLambdaExpression() throws Exception {
    doTest();
  }

  public void testControlFlowAnalysisFailedValueCompatibilityUnchanged() throws Exception {
    doTest();
  }

  public void testNonAccessibleFunctionalInterfaceTypeArguments() throws Exception {
    doTest();
  }

  //JDK-8043374
  public void testIntersectionTypeOfDifferentParameterizationOfTheSameClassInNonWildcardParameterization() throws Exception {
    doTest();
  }

  public void testPreserveCapturedWildcardsDuringNonWildcardParameterization() throws Exception {
    doTest();
  }

  public void testIDEA136401() throws Exception {
    doTest();
  }

  public void testIDEA133920() throws Exception {
    doTest();
  }

  public void testIDEA132253() throws Exception {
    doTest();
  }

  public void testIDEA144840() throws Exception {
    doTest();
  }

  public void testRecursiveAtSiteSubstitutorsWithAdditionalConstraints() throws Exception {
    doTest();
  }

  public void testIDEA136325() throws Exception {
    doTest();
  }

  public void testIDEA127215() throws Exception {
    doTest();
  }

  public void testGroundTargetTypeForExpectedReturnTypeOfLambdaExpression() throws Exception {
    doTest();
  }

  public void testIDEA149224() throws Exception {
    doTest();
  }

  public void testIDEA149670() throws Exception {
    doTest();
  }

  public void testIDEA149709() throws Exception {
    doTest();
  }

  public void testResolveOrderShouldTakeIntoAccountDependenciesOfAlreadyResolvedVars() throws Exception {
    doTest();
  }

  public void testCodeBlockLambdaWithIsValueCompatibleChecks() throws Exception {
    doTest();
  }

  public void testCodeBlockLambdaWithoutParamsIsValueCompatibleChecks() throws Exception {
    doTest();
  }

  public void testInferenceFromReturnLambdaStatementWhereContainingMethodNonGeneric() throws Exception {
    doTest();
  }

  private void doTest() {
    doTest(false);
  }

  private void doTest(boolean warnings) {
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, getModule(), getTestRootDisposable());
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", warnings, false);
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk18();
  }
/*
  public static Test suite() {
    final TestSuite suite = new TestSuite();
    for (int i = 0; i < 1000; i++) {
      suite.addTestSuite(NewLambdaHighlightingTest.class);
    }
    return suite;
  }*/
}