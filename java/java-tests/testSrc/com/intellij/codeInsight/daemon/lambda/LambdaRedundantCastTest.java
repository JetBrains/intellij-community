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
  public void testCastToPrimitive() throws Exception {
    doTest();
  }

  public void testAnotherInterfaceMethodIsPointed() throws Exception {
    doTest();
  }

  public void testSerializableLambda() throws Exception {
    doTest();
  }

  public void testWithAnonymousClasses() throws Exception {
    doTest();
  }


  public void testCastInMethodCallQualifierWithWildcardReturn() throws Exception {
    doTest();
  }

  public void testCapturedWildcardInCast() throws Exception {
    doTest();
  }

  public void testTopLevelResolutionFailures() throws Exception {
    doTest();
  }

  public void testEnumConstantWithFunctionalExpressionArg() throws Exception { doTest(); }
  public void testSecondLevelOverload() throws Exception { doTest(); }
  public void testStopAtMemberLevelDuringWalkUp() throws Exception { doTest(); }

  public void testIDEA154861() { doTest();}

  public void testQualifierOfMethodWithCast() throws Exception {
    doTest();
  }

  public void testInvalidResolveWithoutCast() { doTest();}
  public void testCastInConditionalBranch() { doTest();}

  public void testRejectReturnTypeChange() throws Exception {
    doTest();
  }

  public void testInvalidConditional() throws Exception {
    doTest();
  }

  private void doTest() {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", true, false);
  }
}
