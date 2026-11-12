// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.extractIncrement;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class ExtractIncrementAndDecrementTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp = ippFixture(projectFixture, "extractIncrement");

  @Test
  public void testPostfixDecrement() {doExtractTest("--");}

  @Test
  public void testPostfixIncrement() {doExtractTest("++");}

  @Test
  public void testPrefixDecrement() {doExtractTest("--");}

  @Test
  public void testPrefixIncrement() {doExtractTest("++");}

  @Test
  public void testSwitchExpression() {doExtractTest("++");}

  @Test
  public void testYieldStatement() {doExtractTest("++");}

  @Test
  public void testSingleDoWhileBody() {doExtractTest("++");}

  @Test
  public void testDecrementInVoidContext() {doNegativeTest("--");}

  @Test
  public void testTwoIncrementsInForUpdate() {doNegativeTest("++");}

  @Test
  public void testLambdaExpression() {doExtractTest("++");} //

  @Test
  public void testThrowStatement() {doExtractTest("++");}

  @Test
  public void testForInitialization() {doNegativeTest("++");}

  @Test
  public void testForCondition() {doExtractTest("++");}

  @Test
  public void testDecrementInForUpdate() {doNegativeTest("--");}

  @Test
  public void testForWithoutBraces() {doExtractTest("++");}

  private void doExtractTest(@NotNull String operator) {
    ipp.get().doTest(getMessage(operator));
  }

  private void doNegativeTest(@NotNull String operator) {
    LightJavaCodeInsightTestFixture myFixture = ipp.get().getFixture();
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    IntentionAction intentionAction = CodeInsightTestUtil.findIntentionByText(myFixture.getAvailableIntentions(), getMessage(operator));
    assertNull(intentionAction, intentionAction + " is not expected here");
  }

  @NotNull
  private static String getMessage(@NotNull String operator) {
    return IntentionPowerPackBundle.message("extract.increment.intention.name", operator);
  }

}
