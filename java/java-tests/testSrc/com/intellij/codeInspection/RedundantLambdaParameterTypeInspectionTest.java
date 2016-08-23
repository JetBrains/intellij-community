/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http:public void testwww() {}.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.lambda.RedundantLambdaParameterTypeInspection;
import com.intellij.idea.Bombed;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

import java.util.Calendar;
import java.util.List;

public class RedundantLambdaParameterTypeInspectionTest extends LightCodeInsightFixtureTestCase {
  private RedundantLambdaParameterTypeInspection myInspection = new RedundantLambdaParameterTypeInspection();
  private String myIntentionName = "Remove redundant parameter types";

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInspection/redundantLambdaParameterType";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    ModuleRootModificationUtil.setModuleSdk(myModule, IdeaTestUtil.getMockJdk18());
    myFixture.enableInspections(myInspection);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myFixture.disableInspections(myInspection);
    }
    finally {
      super.tearDown();
    }
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

  public void _testInChain() { // disabled till the functionality is available
    doTest();
  }

  private void doTest() {
    myFixture.configureByFiles(getTestName(false) + ".java");
    final IntentionAction singleIntention = myFixture.findSingleIntention(myIntentionName);
    myFixture.launchAction(singleIntention);
    myFixture.checkResultByFile(getTestName(false) + ".java", getTestName(false) + "_after.java", true);
  }

  private void assertIntentionNotAvailable() {
    myFixture.configureByFiles(getTestName(false) + ".java");
    final List<IntentionAction> intentionActions = myFixture.filterAvailableIntentions(myIntentionName);
    assertEmpty(myIntentionName + " is not expected", intentionActions);
  }
}