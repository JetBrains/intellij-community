// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.redundancy;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class StringCompareToEqualityInspectionTest extends LightJavaInspectionTestCase {

  public void testStringCompareToEquality() {
    doTest();
  }

  public void testStringCompareToEqualityFix() {
    doTest();
    checkQuickFix("Replace with 'equals()'");
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new StringCompareToEqualityInspection();
  }
}
