// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.parenthesis;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.style.UnnecessaryParenthesesInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class UnnecessaryParenthesesQuickFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new UnnecessaryParenthesesInspection()), "parentheses",
                        InspectionGadgetsBundle.message("unnecessary.parentheses.remove.quickfix"));

  @Test
  public void testPolyadic() { ig.get().doTest(); }
  @Test
  public void testPolyadic2() { ig.get().doTest(); }
  @Test
  public void testPolyadic3() { ig.get().doTest(); }
  @Test
  public void testPolyadic4() { ig.get().doTest(); }
  @Test
  public void testCommutative() { ig.get().doTest(); }
  @Test
  public void testWrapping() { ig.get().doTest(); }
  @Test
  public void testNotCommutative() { ig.get().assertQuickfixNotAvailable(); }
  @Test
  public void testStringParentheses() { ig.get().assertQuickfixNotAvailable(); }
  @Test
  public void testComparisonParentheses() { ig.get().assertQuickfixNotAvailable(); }
  @Test
  public void testNotCommutative2() { ig.get().doTest(); }
  @Test
  public void testArrayInitializer() { ig.get().doTest(); }
  @Test
  public void testArrayAccessExpression() { ig.get().doTest(); }
  @Test
  public void testArrayAccessExpression2() { ig.get().doTest(); }
  @Test
  public void testSimplePrecedence() { ig.get().assertQuickfixNotAvailable(); }
  @Test
  public void testLambdaQualifier() { ig.get().assertQuickfixNotAvailable(); }
  @Test
  public void testLambdaInTernary() { ig.get().doTest(); }
  @Test
  public void testLambdaCast() { ig.get().doTest(); }
  @Test
  public void testLambdaBody() { ig.get().doTest(); }
  @Test
  public void testDivision() { ig.get().doTest(); }
  @Test
  public void testDivision2() { ig.get().doTest(); }
  @Test
  public void testSwitchExpression() { ig.get().doTest(); }

}