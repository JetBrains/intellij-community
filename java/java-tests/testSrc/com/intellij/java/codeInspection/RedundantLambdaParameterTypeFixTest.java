// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.lambda.RedundantLambdaParameterTypeInspection;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;

@TestApplication
public class RedundantLambdaParameterTypeFixTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private static final String ourIntentionName = "Remove redundant parameter types";

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/codeInspection/redundantLambdaParameterType");
    myFixture.enableInspections(new RedundantLambdaParameterTypeInspection());
  }

  @Test
  public void testAssignment() {
    doTest();
  }

  @Test
  public void testAssignmentNoParams() {
    assertIntentionNotAvailable();
  }

  @Test
  public void testAssignmentNoTypes() {
    assertIntentionNotAvailable();
  }

  @Test
  public void testAtVarargPlace() {
    assertIntentionNotAvailable();
  }

  @Test
  public void testCallNoTypeArgs() {
    assertIntentionNotAvailable();
  }

  @Test
  public void testCallNoTypeArgs1() {
    assertIntentionNotAvailable();
  }

  @Test
  public void testCallNoTypeArgs2() {
    assertIntentionNotAvailable();
  }

  @Test
  public void testCallWithTypeArgs() {
    doTest();
  }

  @Test
  public void testInferredFromOtherArgs() {
    doTest();
  }

  @Test
  public void testNoSelfTypeParam() {
    doTest();
  }

  @Test
  public void testTypeParam() {
    assertIntentionNotAvailable();
  }

  @Test
  public void testInChain() { // disabled till the functionality is available
    doTest();
  }

  @Test
  public void testNotApplicableDueToChainedCall() {
    assertIntentionNotAvailable();
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles(myFixture.getTestName(false) + ".java");
    final IntentionAction singleIntention = myFixture.findSingleIntention(ourIntentionName);
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.launchAction(singleIntention);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      myFixture.checkResultByFile(myFixture.getTestName(false) + ".java", myFixture.getTestName(false) + "_after.java", true);
    });
  }

  private void assertIntentionNotAvailable() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFiles(myFixture.getTestName(false) + ".java");
    final List<IntentionAction> intentionActions = myFixture.filterAvailableIntentions(ourIntentionName);
    assertThat(intentionActions).as(ourIntentionName + " is not expected").isEmpty();
  }
}