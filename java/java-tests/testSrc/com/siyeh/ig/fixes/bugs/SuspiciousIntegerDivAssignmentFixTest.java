// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.bugs;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.bugs.SuspiciousIntegerDivAssignmentInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class SuspiciousIntegerDivAssignmentFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new SuspiciousIntegerDivAssignmentInspection()), "bugs/suspicious_integer_div_assignment",
                        InspectionGadgetsBundle.message("suspicious.integer.div.assignment.quickfix"));

  @Test
  public void testDivAssignmentOperator() {
    ig.get().doTest();
  }

  @Test
  public void testMultAssignmentOperator() {
    ig.get().doTest();
  }

  @Test
  public void testCharacterDivision() {
    ig.get().doTest();
  }

  @Test
  public void testParenthesizedRhs() {
    ig.get().doTest();
  }

}
