// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.controlflow;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.controlflow.DoubleNegationInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class DoubleNegationFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new DoubleNegationInspection()), "controlflow/double_negation",
                        InspectionGadgetsBundle.message("double.negation.quickfix"));

  @Test
  public void testDoubleNegation() {
    ig.get().doExpressionTest(ig.get().getDefaultHint(),
                     "!/**/!(System.currentTimeMillis() > 1_000_000)",
                     "System.currentTimeMillis() > 1_000_000");
  }

  @Test
  public void testDoubleDoubleNegation() { ig.get().doTest(); }

  @Test
  public void testPolyadicParentheses() { ig.get().doTest(); }

  @Test
  public void testDoNotCleanupWithSingleNegation() {
    ig.get().assertQuickfixNotAvailable(ig.get().getDefaultHint(),
                               """
                                 class X {
                                   boolean test(boolean isValid) {
                                     return !/**/isValid;
                                   }
                                 }
                                 """);
  }
}
