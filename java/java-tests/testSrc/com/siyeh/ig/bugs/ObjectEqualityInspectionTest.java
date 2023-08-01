// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class ObjectEqualityInspectionTest extends LightJavaInspectionTestCase {

  public void testObjectEquality() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final ObjectEqualityInspection inspection = new ObjectEqualityInspection();
    inspection.m_ignorePrivateConstructors = true;
    return inspection;
  }
}