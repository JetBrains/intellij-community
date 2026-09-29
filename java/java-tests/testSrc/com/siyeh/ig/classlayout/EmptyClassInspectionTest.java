// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class EmptyClassInspectionTest extends LightJavaInspectionTestCase {

  public void testEmptyClass() {
    doTest();
  }

  public void testPackageInfo() {
    doNamedTest("package-info");
  }

  public void testEmptyFile() {
    doTest();
  }

  public void testClassWithComments() {
    doTest();
  }

  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    final EmptyClassInspection inspection = new EmptyClassInspection();
    inspection.ignoreClassWithParameterization = true;
    inspection.ignoreThrowables = true;
    inspection.commentsAreContent = true;
    return inspection;
  }
}