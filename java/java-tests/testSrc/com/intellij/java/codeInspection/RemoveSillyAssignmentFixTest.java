// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.sillyAssignment.SillyAssignmentInspection;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

/**
 * @author Bas Leijdekkers
 */
public class RemoveSillyAssignmentFixTest extends LightJavaCodeInsightFixtureTestCase {

  public void testRemoveCompleteAssignment() { doTest(); }
  public void testKeepReference() { doTest(); }
  public void testFieldAssignsItself() { doTest(); }
  public void testFieldKeepInitializer() { doTest(); }
  public void testSillyButIncomplete() { doTest(); }
  public void testArrayElement1() { doTest(); }
  public void testArrayElement2() { doTest(); }

  public void testFinalField() { assertQuickfixNotAvailable(); }
  public void testFinalField2() { assertQuickfixNotAvailable(); }

  public void doTest() {
    myFixture.enableInspections(SillyAssignmentInspection.class);
    myFixture.configureByFile(getTestName(false) + ".java");
    final IntentionAction intention = myFixture.findSingleIntention(JavaBundle.message("assignment.to.itself.quickfix.name"));
    assertNotNull(intention);
    myFixture.launchAction(intention);
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    myFixture.checkResultByFile(getTestName(false) + ".after.java");
  }

  protected void assertQuickfixNotAvailable() {
    myFixture.enableInspections(SillyAssignmentInspection.class);
    final String quickfixName = JavaBundle.message("assignment.to.itself.quickfix.name");
    myFixture.configureByFile(getTestName(false) + ".java");
    assertEmpty("Quickfix '" + quickfixName + "' is available unexpectedly", myFixture.filterAvailableIntentions(quickfixName));
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInspection/removeSillyAssignmentFix";
  }
}
