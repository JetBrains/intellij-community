// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.lambda.RedundantLambdaParameterTypeInspection;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class RedundantLambdaParameterTypeFixTest extends LightJavaCodeInsightFixtureTestCase {
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
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    myFixture.checkResultByFile(getTestName(false) + ".java", getTestName(false) + "_after.java", true);
  }

  private void assertIntentionNotAvailable() {
    myFixture.configureByFiles(getTestName(false) + ".java");
    final List<IntentionAction> intentionActions = myFixture.filterAvailableIntentions(ourIntentionName);
    assertEmpty(ourIntentionName + " is not expected", intentionActions);
  }
}