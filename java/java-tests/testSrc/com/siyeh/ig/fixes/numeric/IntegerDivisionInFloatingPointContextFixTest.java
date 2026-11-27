// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.numeric;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.numeric.IntegerDivisionInFloatingPointContextInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class IntegerDivisionInFloatingPointContextFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new IntegerDivisionInFloatingPointContextInspection()),
                        "numeric/integer_division_in_floating_point_context",
                        InspectionsBundle.message("fix.all.inspection.problems.in.file", InspectionGadgetsBundle.message("integer.division.in.floating.point.context.display.name")));

  @Test
  public void testSimpleFloat() { ig.get().doTest(); }

  @Test
  public void testSimpleDouble() { ig.get().doTest(); }

  @Test
  public void testExpectedParenthesizedExpr() { ig.get().doTest(); }

  @Test
  public void testComplexPolyadicExpression() { ig.get().doTest(); }

  @Test
  public void testPolyadicExpression() { ig.get().doTest(); }

}
