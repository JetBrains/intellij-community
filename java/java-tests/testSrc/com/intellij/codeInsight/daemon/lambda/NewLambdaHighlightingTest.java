/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class NewLambdaHighlightingTest extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/newLambda";

  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new UnusedSymbolLocalInspection(),
    };
  }

  public void testIDEA93586() throws Exception {
    doTest();
  }

  public void testIDEA113573() throws Exception {
    doTest();
  }

  public void testIDEA112922() throws Exception {
    doTest();
  }

  public void testIDEA113504() throws Exception {
    doTest();
  }
  
  public void testAfterAbstractPipeline2() throws Exception {
    doTest();
  }

  public void testIDEA116252() throws Exception {
    doTest();
  }

  public void testIDEA106670() throws Exception {
    doTest();
  }

  public void testIDEA116548() throws Exception {
    doTest();
  }

  public void testOverloadResolutionSAM() throws Exception {
    doTest();
  }

  public void testIntersectionTypesDuringInference() throws Exception {
    doTest();
  }

  public void testIncludeConstraintsWhenParentMethodIsDuringCalculation() throws Exception {
    doTest();
  }

  public void testUseCalculatedSubstitutor() throws Exception {
    doTest();
  }

  public void testArgumentOfAnonymousClass() throws Exception {
    doTest();
  }

  public void testEllipsis() throws Exception {
    doTest();
  }

  public void testOuterMethodPropagation() throws Exception {
    doTest();
  }

  public void testRecursiveCalls() throws Exception {
    doTest();
  }

  public void testGroundTargetTypeForImplicitLambdas() throws Exception {
    doTest();
  }

  public void testAdditionalConstraintsReduceOrder() throws Exception {
    doTest();
  }

  public void testAdditionalConstraintSubstitution() throws Exception {
    doTest();
  }
  public void testFunctionalInterfacesCalculation() throws Exception {
    doTest();
  }

  public void testMissedSiteSubstitutorDuringDeepAdditionalConstraintsGathering() throws Exception {
    doTest();
  }

  public void testIDEA120992() throws Exception {
    doTest();
  }

  public void testTargetTypeConflictResolverShouldNotTryToEvaluateCurrentArgumentType() throws Exception {
    doTest();
  }

  public void testIDEA119535() throws Exception {
    doTest();
  }

  public void testIDEA119003() throws Exception {
    doTest();
  }

  public void testIDEA117124() throws Exception {
    doTest();
  }

  public void testWildcardParameterization() throws Exception {
    doTest();
  }

  public void testDiamondInLambdaReturn() throws Exception {
    doTest();
  }

  public void testIDEA118965() throws Exception {
    doTest();
  }

  public void testIDEA121315() throws Exception {
    doTest();
  }

  public void testIDEA118965comment() throws Exception {
    doTest();
  }

  public void testIDEA122074() throws Exception {
    doTest();
  }

  public void testIDEA122084() throws Exception {
    doTest();
  }

  public void testAdditionalConstraintDependsOnNonMentionedVars() throws Exception {
    doTest();
  }

  public void testIDEA122616() throws Exception {
    doTest();
  }

  public void testIDEA122700() throws Exception {
    doTest();
  }

  public void testIDEA122406() throws Exception {
    doTest();
  }

  public void testNestedCallsInsideLambdaReturnExpression() throws Exception {
    doTest();
  }

  public void testIDEA123731() throws Exception {
    doTest();
  }

  public void testIDEA123869() throws Exception {
    doTest();
  }

  public void testIDEA123848() throws Exception {
    doTest();
  }

  private void doTest() {
    doTest(false);
  }

  private void doTest(boolean warnings) {
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, getModule(), getTestRootDisposable());
    doTestNewInference(BASE_PATH + "/" + getTestName(false) + ".java", warnings, false);
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk18();
  }
}
