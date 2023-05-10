// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.sameParameterValue.SameParameterValueInspection;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public final class SameParameterValueQuickFixTest extends LightJavaCodeInsightFixtureTestCase {

  public void testSimple() {
    doTest();
  }

  private void doTest() {
    doNamedTest(getTestName(false));
  }

  private void doNamedTest(String name) {
    LocalInspectionTool inspection = new SameParameterValueInspection().getSharedLocalInspectionTool();
    myFixture.enableInspections(inspection);
    myFixture.configureByFile(name + ".java");
    myFixture.testHighlighting(true, false, false);
    final @NotNull List<IntentionAction> intentions = myFixture.filterAvailableIntentions("Inline value");
    assertEquals("intention not found", 1, intentions.size());
    myFixture.checkPreviewAndLaunchAction(intentions.get(0));
    myFixture.checkResultByFile(name + ".after.java");
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/sameParameterValueQuickFix";
  }
}
