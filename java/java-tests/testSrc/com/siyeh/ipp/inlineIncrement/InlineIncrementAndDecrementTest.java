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
package com.siyeh.ipp.inlineIncrement;

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

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public class InlineIncrementAndDecrementTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp = ippFixture(projectFixture, "inlineIncrement");

  @Test
  public void testPostfixDecrementNext() {doInlineTest("--");}
  @Test
  public void testPostfixDecrementPrev() {doInlineTest("--");}

  @Test
  public void testPostfixIncrementNext() {doInlineTest("++");}
  @Test
  public void testPostfixIncrementPrev() {doInlineTest("++");}

  @Test
  public void testPrefixDecrementNext() {doInlineTest("--");}
  @Test
  public void testPrefixDecrementPrev() {doInlineTest("--");}

  @Test
  public void testPrefixIncrementNext() {doInlineTest("++");}
  @Test
  public void testPrefixIncrementPrev() {doInlineTest("++");}

  @Test
  public void testPrefixIncrementBoth() {doInlineTest("++");}
  @Test
  public void testPrefixDecrementBoth() {doInlineTest("--");}

  @Test
  public void testLambdaBlock() {doInlineTest("++");}
  @Test
  public void testLambdaExpression() {doNegativeTest("++");}

  @Test
  public void testParenthesesNext() {doInlineTest("--");}
  @Test
  public void testParenthesesPrev() {doInlineTest("++");}

  private void doInlineTest(@NotNull String operator) {
    ipp.get().doTest(IntentionPowerPackBundle.message("inline.increment.intention.name", operator));
  }

  private void doNegativeTest(@SuppressWarnings("SameParameterValue") @NotNull String operator) {
    LightJavaCodeInsightTestFixture myFixture = ipp.get().getFixture();
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    List<IntentionAction> availableIntentions = myFixture.getAvailableIntentions();
    String action = IntentionPowerPackBundle.message("inline.increment.intention.name", operator);
    final IntentionAction intentionAction = CodeInsightTestUtil.findIntentionByText(availableIntentions, action);
    assertNull(intentionAction);
  }
}
