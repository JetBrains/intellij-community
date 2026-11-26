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

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class LambdaRedundantCastTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture =
    lightDaemonAnalyzerFixture(projectFixture, () -> List.of(new RedundantCastInspection()));

  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/redundantCast";

  @Test public void testIntersection() { doTest(); }
  @Test public void testSer() { doTest(); }
  @Test public void testLambdaReturnExpressions() { doTest(); }
  @Test public void testLambdaReturnExpressions1() { doTest(); }
  @Test public void testPreserveThisContext() { doTest(); }
  @Test public void testPreventBadReturnTypeInReturnExpression() { doTest(); }
  @Test public void testExpectedTypeByNestedArrayInitializer() { doTest(); }
  @Test
  public void testCastToPrimitive() {
    doTest();
  }

  @Test
  public void testAnotherInterfaceMethodIsPointed() {
    doTest();
  }

  @Test
  public void testSerializableLambda() {
    doTest();
  }

  @Test
  public void testWithAnonymousClasses() {
    doTest();
  }


  @Test
  public void testCastInMethodCallQualifierWithWildcardReturn() {
    doTest();
  }

  @Test
  public void testCapturedWildcardInCast() {
    doTest();
  }

  @Test
  public void testTopLevelResolutionFailures() {
    doTest();
  }

  @Test public void testEnumConstantWithFunctionalExpressionArg() { doTest(); }
  @Test public void testSecondLevelOverload() { doTest(); }
  @Test public void testStopAtMemberLevelDuringWalkUp() { doTest(); }

  @Test public void testIDEA154861() { doTest();}

  @Test
  public void testQualifierOfMethodWithCast() {
    doTest();
  }

  @Test public void testInvalidResolveWithoutCast() { doTest();}
  @Test public void testCastInConditionalBranch() { doTest();}
  @Test public void testCastInsideLambdaReturnExpressionPassedToEnumConstant() { doTest(); }
  @Test public void testGroundTargetTypeDiffersFromCastType() { doTest(); }
  @Test public void testInferenceErrorForApplicableMethod() { doTest(); }
  @Test public void testLambdaWithCastInReturnStatement() { doTest(); }
  @Test public void testCastInNeighbourArgument() { doTest(); }
  @Test public void testErasedTargetType() { doTest(); }
  @Test public void testThrowsStatementInLambdaBody() { doTest(); }
  @Test public void testSynchronizeCasts() { doTest(); }
  @Test public void testBoxingInConditionalOfLambdaReturn() { doTest(); }
  @Test public void testRawMiddleMan() { doTest(); }
  @Test
  public void testRejectReturnTypeChange() {
    doTest();
  }

  @Test
  public void testInvalidConditional() {
    doTest();
  }
  @Test public void testSuperBoundLambda() { doTest(); }
  @Test public void testLambdaReturnChain() { doTest(); }
  @Test public void testExpectedTypeProducesBadReturnType() { doTest(); }
  @Test public void testNonDenotableParent() { doTest(); }
  @Test public void testToBeNonPolyConditional() { doTest(); }

  private void doTest() {
    daemonFixture.get().doTest(BASE_PATH + "/" + daemonFixture.get().getFixture().getTestName(false) + ".java", true, false);
  }
}
