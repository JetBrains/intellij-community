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
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.lambda.RedundantLambdaParameterTypeInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RedundantLambdaParameterTypeInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  private static final String ourIntentionName = "Remove redundant parameter types";

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInspection/redundantLambdaParameterType";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new RedundantLambdaParameterTypeInspection());
  }

  public void testAssignment() {
    doTest();
  }

  public void testAssignmentNoParams() {
    assertIntentionNotAvailable();
  }

  public void testAssignmentNoTypes() {
    assertIntentionNotAvailable();
  }

  public void testAtVarargPlace() {
    assertIntentionNotAvailable();
  }

  public void testCallNoTypeArgs() {
    assertIntentionNotAvailable();
  }

  public void testCallNoTypeArgs1() {
    assertIntentionNotAvailable();
  }

  public void testCallNoTypeArgs2() {
    assertIntentionNotAvailable();
  }

  public void testCallWithTypeArgs() {
    doTest();
  }

  public void testInferredFromOtherArgs() {
    doTest();
  }

  public void testNoSelfTypeParam() {
    doTest();
  }

  public void testTypeParam() {
    assertIntentionNotAvailable();
  }

  public void testInChain() { // disabled till the functionality is available
    doTest();
  }

  public void testNotApplicableDueToChainedCall() {
    assertIntentionNotAvailable();
  }

  private void doTest() {
    myFixture.configureByFiles(getTestName(false) + ".java");
    final IntentionAction singleIntention = myFixture.findSingleIntention(ourIntentionName);
    myFixture.launchAction(singleIntention);
    myFixture.checkResultByFile(getTestName(false) + ".java", getTestName(false) + "_after.java", true);
  }

  private void assertIntentionNotAvailable() {
    myFixture.configureByFiles(getTestName(false) + ".java");
    final List<IntentionAction> intentionActions = myFixture.filterAvailableIntentions(ourIntentionName);
    assertEmpty(ourIntentionName + " is not expected", intentionActions);
  }
}