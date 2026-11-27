// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.siyeh.ig.fixes.dataflow;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.dataflow.ReuseOfLocalVariableInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class ReuseOfLocalVariableInspectionFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new ReuseOfLocalVariableInspection()), "dataflow/reuse_of_local");

  @Test
  public void testSimple() { ig.get().doTest(InspectionGadgetsBundle.message("reuse.of.local.variable.split.quickfix")); }
  @Test
  public void testParentheses() { ig.get().doTest(InspectionGadgetsBundle.message("reuse.of.local.variable.split.quickfix")); }
  @Test
  public void testSwitchExpression() { ig.get().doTest(InspectionGadgetsBundle.message("reuse.of.local.variable.split.quickfix")); }
  @Test
  public void testIfNoBlock() { ig.get().doTest(InspectionGadgetsBundle.message("reuse.of.local.variable.split.quickfix")); }
}
