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
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class ExceptionVariablesInferenceTest {
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/exceptions/";

  @Test public void testUnboundExceptions() { doTest(); }
  @Test public void testNotThrownLambdaBodyReferencedMethod() { doTest(); }
  @Test public void testNotThrownLambdaBodyReferencedMethodContradictBounds() { doTest(); }
  @Test public void testLambdaParamExceptionVariable() { doTest(); }
  @Test public void testIDEA116441() { doTest(); }
  @Test public void testLambdaBodyUncaughtExceptionsForOuterCallInference() { doTest(); }
  @Test public void testMethodRefUncaughtExceptionsForOuterCallInference() { doTest(); }
  @Test public void testExceptionsThrownByCallArguments() { doTest(); }

  private void doTest() {
    daemonFixture.get().doTest(BASE_PATH + daemonFixture.get().getFixture().getTestName(false) + ".java", false, false);
  }
}