// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.redundancy;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CompareToEqualityInspectionTest extends LightJavaInspectionTestCase {

  public void testCompareToEquality() {
    doTest();
  }

  public void testCompareToEqualityFix() {
    doTest();
    checkQuickFixAll();
  }

  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new CompareToEqualityInspection();
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }
}
