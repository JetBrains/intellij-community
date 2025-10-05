// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  public void testSimple() { doTest(); }
  public void testCastedValue() { doTest(); }
  public void testStringThatNeedsEscaping() { doTest(false, false); }
  public void testLongValue() { doTest(); }
  public void testVararg() { doTest(); }

  private void doTest() {
    doTest(true, false);
  }

  private void doTest(boolean testHighlighting, boolean quickFixNotAvailable) {
    String name = getTestName(false);
    SameParameterValueInspection globalInspection = new SameParameterValueInspection();
    globalInspection.ignoreWhenRefactoringIsComplicated = false;
    LocalInspectionTool inspection = globalInspection.getSharedLocalInspectionTool();
    myFixture.enableInspections(inspection);
    myFixture.configureByFile(name + ".java");
    if (testHighlighting) myFixture.testHighlighting(true, false, false);
    int caretOffset = myFixture.getCaretOffset();
    assertTrue("No <caret> found in '" + name + ".java'", caretOffset != 0);
    final @NotNull List<IntentionAction> intentions = myFixture.filterAvailableIntentions("Inline value");
    if (quickFixNotAvailable) {
      assertEmpty("intention found when it should not be available", intentions);
    }
    else {
      assertEquals("intention not found", 1, intentions.size());
      myFixture.checkPreviewAndLaunchAction(intentions.get(0));
      myFixture.checkResultByFile(name + ".after.java");
    }
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/sameParameterValueQuickFix";
  }
}
