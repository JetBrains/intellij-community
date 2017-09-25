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
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class LambdaRedundantCastTest extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/redundantCast";

  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new RedundantCastInspection()
    };
  }

  public void testIntersection() { doTest(); }
  public void testSer() { doTest(); }
  public void testLambdaReturnExpressions() { doTest(); }
  public void testLambdaReturnExpressions1() { doTest(); }
  public void testPreventBadReturnTypeInReturnExpression() { doTest(); }
  public void testExpectedTypeByNestedArrayInitializer() { doTest(); }
  public void testCastToPrimitive() {
    doTest();
  }

  public void testAnotherInterfaceMethodIsPointed() {
    doTest();
  }

  public void testSerializableLambda() {
    doTest();
  }

  public void testWithAnonymousClasses() {
    doTest();
  }


  public void testCastInMethodCallQualifierWithWildcardReturn() {
    doTest();
  }

  public void testCapturedWildcardInCast() {
    doTest();
  }

  public void testTopLevelResolutionFailures() {
    doTest();
  }

  public void testEnumConstantWithFunctionalExpressionArg() { doTest(); }
  public void testSecondLevelOverload() { doTest(); }
  public void testStopAtMemberLevelDuringWalkUp() { doTest(); }

  public void testIDEA154861() { doTest();}

  public void testQualifierOfMethodWithCast() {
    doTest();
  }

  public void testInvalidResolveWithoutCast() { doTest();}
  public void testCastInConditionalBranch() { doTest();}
  public void testCastInsideLambdaReturnExpressionPassedToEnumConstant() { doTest(); }
  public void testGroundTargetTypeDiffersFromCastType() { doTest(); }
  public void testRejectReturnTypeChange() {
    doTest();
  }

  public void testInvalidConditional() {
    doTest();
  }

  private void doTest() {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", true, false);
  }
}
