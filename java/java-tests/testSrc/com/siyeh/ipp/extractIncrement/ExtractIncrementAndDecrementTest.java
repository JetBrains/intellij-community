// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ipp.extractIncrement;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;
import org.jetbrains.annotations.NotNull;

public class ExtractIncrementAndDecrementTest extends IPPTestCase {

  public void testPostfixDecrement() {doExtractTest("--");}

  public void testPostfixIncrement() {doExtractTest("++");}

  public void testPrefixDecrement() {doExtractTest("--");}

  public void testPrefixIncrement() {doExtractTest("++");}

  public void testSwitchExpression() {doExtractTest("++");}

  public void testYieldStatement() {doExtractTest("++");}

  public void testSingleDoWhileBody() {doExtractTest("++");}

  public void testDecrementInVoidContext() {doNegativeTest("--");}

  public void testTwoIncrementsInForUpdate() {doNegativeTest("++");}

  public void testLambdaExpression() {doExtractTest("++");} //

  public void testThrowStatement() {doExtractTest("++");}

  public void testForInitialization() {doNegativeTest("++");}

  public void testForCondition() {doExtractTest("++");}

  public void testDecrementInForUpdate() {doNegativeTest("--");}

  public void testForWithoutBraces() {doExtractTest("++");}

  private void doExtractTest(@NotNull String operator) {
    super.doTest(getMessage(operator));
  }

  private void doNegativeTest(@NotNull String operator) {
    myFixture.configureByFile(getTestName(false) + ".java");
    IntentionAction intentionAction = CodeInsightTestUtil.findIntentionByText(myFixture.getAvailableIntentions(), getMessage(operator));
    assertNull(intentionAction + " is not expected here", intentionAction);
  }

  @NotNull
  private static String getMessage(@NotNull String operator) {
    return IntentionPowerPackBundle.message("extract.increment.intention.name", operator);
  }

  @Override
  protected String getRelativePath() {
    return "extractIncrement";
  }

}
