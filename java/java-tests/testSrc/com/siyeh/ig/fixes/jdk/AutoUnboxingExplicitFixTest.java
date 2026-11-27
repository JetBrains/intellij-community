// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.jdk;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.jdk.AutoUnboxingInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class AutoUnboxingExplicitFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new AutoUnboxingInspection()), "jdk/auto_unboxing",
                        InspectionGadgetsBundle.message("auto.unboxing.make.unboxing.explicit.quickfix"));

  @Test
  public void testCommentsInTypeCast() { ig.get().doTest(); }
  @Test
  public void testSwitchExpressionPrecedence() { ig.get().doTest(); }
  @Test
  public void testMethodCallPrecedence() { ig.get().doTest(); }
  @Test
  public void testExplicitTypeArguments() { ig.get().doTest(); }
  @Test
  public void testExplicitTypeArguments2() { ig.get().doTest(); }
  @Test
  public void testParentheses() { ig.get().doTest(); }
}