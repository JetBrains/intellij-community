// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.style;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.style.UnclearBinaryExpressionInspection;

/**
 * @author Bas Leijdekkers
 */
public class UnclearBinaryExpressionFixTest extends IGQuickFixesTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new UnclearBinaryExpressionInspection());
    myRelativePath = "style/unclear_binary_expression";
    myDefaultHint = InspectionGadgetsBundle.message("unclear.binary.expression.quickfix");
  }

  @Override
  protected void doTest() {
    // epic fail - two same named intentions
    final String testName = getTestName(false);
    myFixture.configureByFile(getRelativePath() + "/" + testName + ".java");
    for (IntentionAction action : myFixture.filterAvailableIntentions(myDefaultHint)) {
      action = IntentionActionDelegate.unwrap(action);
      if (QuickFixWrapper.unwrap(action) != null) {
        myFixture.launchAction(action);
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
        myFixture.checkResultByFile(getRelativePath() + "/" + testName + ".after.java");
        return;
      }
    }
    fail();
  }

  public void testSimpleAssignment() { doTest(); }

  public void testBrokenCode() { assertQuickfixNotAvailable(); }
}
