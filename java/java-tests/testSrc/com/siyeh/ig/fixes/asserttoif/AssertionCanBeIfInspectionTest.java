// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.asserttoif;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.asserttoif.AssertionCanBeIfInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class AssertionCanBeIfInspectionTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new AssertionCanBeIfInspection()), "asserttoif/assert_to_if",
                        InspectionGadgetsBundle.message("assert.can.be.if.quickfix"));

  @Test
  public void testIncomplete() { ig.get().assertQuickfixNotAvailable(); }
  @Test
  public void testIncomplete2() { ig.get().assertQuickfixNotAvailable(); }
  @Test
  public void testMessage() { ig.get().doTest(); }
  @Test
  public void testNeedsCodeBlock() { ig.get().doTest(); }
}
