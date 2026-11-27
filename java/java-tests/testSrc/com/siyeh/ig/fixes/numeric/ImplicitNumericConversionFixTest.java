// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.numeric;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.numeric.ImplicitNumericConversionInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class ImplicitNumericConversionFixTest {
  private final TestFixture<IGQuickFixesFixture> ig = igQuickFixesFixture(() -> {
    ImplicitNumericConversionInspection inspection = new ImplicitNumericConversionInspection();
    inspection.ignoreCharConversions = true;
    return List.of(inspection);
  }, "numeric/implicit_numeric_conversion", InspectionGadgetsBundle.message("implicit.numeric.conversion.make.explicit.quickfix"));

  @Test
  public void testOperatorAssignment() { ig.get().doTest(); }
  @Test
  public void testOperatorAssignment2() { ig.get().doTest(); }
  @Test
  public void testOperatorAssignment3() { ig.get().doTest(); }
  @Test
  public void testOperatorAssignment4() { ig.get().doTest(); }
  @Test
  public void testPlainAssignment() { ig.get().doTest(); }
  @Test
  public void testHexadecimalLiteral() { ig.get().doTest(); }
  @Test
  public void testParentheses() { ig.get().doTest("Convert to '100L'"); }
  @Test
  public void testArrayAccess() { ig.get().doTest(); }
  @Test
  public void testPrecedence() { ig.get().doTest(); }
  @Test
  public void testCharConversion() { ig.get().doTest(); }
  @Test
  public void testCharConversion2() { ig.get().doTest(); }

}
