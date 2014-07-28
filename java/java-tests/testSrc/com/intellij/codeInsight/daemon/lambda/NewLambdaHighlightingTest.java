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
      new UnusedSymbolLocalInspection()
    };
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
  public void testIDEA126109() { doTest(); }
  public void testIDEA126809() { doTest(); }

  public void testIDEA127596() throws Exception {
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
