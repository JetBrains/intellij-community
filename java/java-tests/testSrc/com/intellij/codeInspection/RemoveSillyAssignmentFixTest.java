/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.sillyAssignment.SillyAssignmentInspection;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

/**
 * @author Bas Leijdekkers
 */
public class RemoveSillyAssignmentFixTest extends LightCodeInsightFixtureTestCase {

  public void testRemoveCompleteAssignment() { doTest(); }
  public void testKeepReference() { doTest(); }
  public void testFieldAssignsItself() { doTest(); }
  public void testFieldKeepInitializer() { doTest(); }
  public void testSillyButIncomplete() { doTest(); }

  public void testFinalField() { assertQuickfixNotAvailable(); }

  public void doTest() {
    myFixture.enableInspections(SillyAssignmentInspection.class);
    myFixture.configureByFile(getTestName(false) + ".java");
    final IntentionAction intention = myFixture.findSingleIntention(InspectionsBundle.message("assignment.to.itself.quickfix.name"));
    assertNotNull(intention);
    myFixture.launchAction(intention);
    myFixture.checkResultByFile(getTestName(false) + ".after.java");
  }

  protected void assertQuickfixNotAvailable() {
    myFixture.enableInspections(SillyAssignmentInspection.class);
    final String quickfixName = InspectionsBundle.message("assignment.to.itself.quickfix.name");
    myFixture.configureByFile(getTestName(false) + ".java");
    assertEmpty("Quickfix \'" + quickfixName + "\' is available unexpectedly", myFixture.filterAvailableIntentions(quickfixName));
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInspection/removeSillyAssignmentFix";
  }
}
