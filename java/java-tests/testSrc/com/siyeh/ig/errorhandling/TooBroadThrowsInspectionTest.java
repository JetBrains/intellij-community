// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.errorhandling;

import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class TooBroadThrowsInspectionTest {
  private final TestFixture<IGQuickFixesFixture> ig = igQuickFixesFixture(() -> {
    TooBroadThrowsInspection inspection = new TooBroadThrowsInspection();
    inspection.hiddenExceptionsThreshold = 5;
    return List.of(inspection);
  }, "toobroadthrows", InspectionGadgetsBundle.message("overly.broad.throws.clause.quickfix2"));

  @Test
  public void testNotFoundInsteadOfIOException() { ig.get().doTest(); }
  @Test
  public void testThrowNull() { ig.get().doTest(); }
  @Test
  public void testGenericThrow() { ig.get().doTest(); }
  @Test
  public void testMultiCatch() { ig.get().doTest(InspectionGadgetsBundle.message("overly.broad.throws.clause.quickfix1")); }

  @Test
  public void testMoreExceptionsThanThreshold() { ig.get().doTest(); }
}