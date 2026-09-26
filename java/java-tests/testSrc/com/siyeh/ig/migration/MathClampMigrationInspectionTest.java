// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.migration;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;

public final class MathClampMigrationInspectionTest extends LightJavaInspectionTestCase {

  public void testMathClampMigration() {
    doTest();
    checkQuickFixAll();
  }

  public void testInvalidInputs() {
    doTest();
  }

  public void testFullyQualifiedName() {
    doTest();
    checkQuickFixAll();
  }

  public void testMathClampBraveMode() {
    doTest();
    checkQuickFixAll();
  }
  
  @Override
  protected InspectionProfileEntry getInspection() {
    MathClampMigrationInspection inspection = new MathClampMigrationInspection();
    if (!this.getTestName(false).contains("BraveMode")) {
      inspection.braveMode = false;
    }
    return inspection;
  }
}
