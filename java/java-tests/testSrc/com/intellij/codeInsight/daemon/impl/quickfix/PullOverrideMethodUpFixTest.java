// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.codeInsight.intention.IntentionAction;

public class PullOverrideMethodUpFixTest extends LightQuickFixTestCase {
  public void test1() {
    doSingleTest("1.java");
  }

  public void test2() {
    doSingleTest("2.java");
  }

  public void test3() {
    doSingleTest("3.java");
  }

  public void test4() {
    doSingleTest("4.java");
  }

  public void test6() {
    doSingleTest("6.java");
  }

  public void testRefactoringIntentionsAvailable() {
    doTestActionAvailable(5, "Pull members up");
    doTestActionAvailable(5, "Extract interface");
    doTestActionAvailable(5, "Extract superclass");
  }

  private void doTestActionAvailable(final int suffix, final String actionText) {
    final String testFullPath = getBasePath() + "/before" + suffix + ".java";
    configureByFile(testFullPath);
    doHighlighting();
    final IntentionAction action = findActionWithText(actionText);
    assertNotNull(action);
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/pullUp";
  }
}
