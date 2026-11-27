// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.numeric;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.numeric.LossyConversionCompoundAssignmentInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class LossyConversionCompoundAssignmentFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new LossyConversionCompoundAssignmentInspection()), "numeric/lossy_conversion_compound_assignment");

  @Test
  public void testLongTimesDouble() {
    ig.get().doTest(InspectionGadgetsBundle.message(
      "inspection.lossy.conversion.compound.assignment.expand.fix.name", "long"));
  }

  @Test
  public void testLongMultiplyDouble() {
    ig.get().doTest(QuickFixBundle.message("add.typecast.cast.text", "long",
                                  QuickFixBundle.message("fix.expression.role.expression")));
  }

  @Test
  public void testBytePlusShortNotExpected() {
    ig.get().assertQuickfixNotAvailable(QuickFixBundle.message("add.typecast.cast.text", "byte",
                                  QuickFixBundle.message("fix.expression.role.expression")));
  }

}
