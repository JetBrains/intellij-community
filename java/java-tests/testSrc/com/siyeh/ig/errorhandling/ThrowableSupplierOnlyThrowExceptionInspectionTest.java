// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.errorhandling;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtilRt;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixture.INSPECTION_GADGETS_TEST_DATA_PATH;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class ThrowableSupplierOnlyThrowExceptionInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new ThrowableSupplierOnlyThrowExceptionInspection(), ArrayUtilRt.EMPTY_STRING_ARRAY,
      INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/errorhandling/throwable_supplier_only_throw_exception");

  @Test
  public void testSimple() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFix(InspectionGadgetsBundle.message("throwable.supplier.only.throw.exception.quickfix"));
  }

  @Test
  public void testSeveral() {
    inspectionFixture.get().doTest();
    inspectionFixture.get().checkQuickFixAll();
  }
}