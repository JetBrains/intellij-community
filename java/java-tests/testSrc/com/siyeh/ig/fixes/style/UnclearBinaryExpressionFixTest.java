// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.style;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.style.UnclearBinaryExpressionInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class UnclearBinaryExpressionFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new UnclearBinaryExpressionInspection()), "style/unclear_binary_expression",
                        InspectionGadgetsBundle.message("unclear.binary.expression.quickfix"));

  private void doTest() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
    // epic fail - two same named intentions
    final String testName = ig.get().getTestName(false);
    myFixture.configureByFile(ig.get().getRelativePath() + "/" + testName + ".java");
    EdtTestUtil.runInEdtAndWait(() -> {
      for (IntentionAction action : myFixture.filterAvailableIntentions(ig.get().getDefaultHint())) {
        action = IntentionActionDelegate.unwrap(action);
        if (QuickFixWrapper.unwrap(action) != null) {
          myFixture.launchAction(action);
          NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
          myFixture.checkResultByFile(ig.get().getRelativePath() + "/" + testName + ".after.java");
          return;
        }
      }
      fail();
    });
  }

  @Test
  public void testSimpleAssignment() { doTest(); }

  @Test
  public void testBrokenCode() { ig.get().assertQuickfixNotAvailable(); }
}
