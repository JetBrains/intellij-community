// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.migration;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;

public final class ComparatorMinMaxCanBeUsedInspectionTest extends LightJavaInspectionTestCase {

  public void testComparatorMinMaxCanBeUsed() {
    doTest();
    checkQuickFixAll();
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new ComparatorMinMaxCanBeUsedInspection();
  }
}
