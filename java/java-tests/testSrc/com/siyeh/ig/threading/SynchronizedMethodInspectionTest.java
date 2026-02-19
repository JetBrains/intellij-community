// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.threading;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class SynchronizedMethodInspectionTest extends LightJavaInspectionTestCase {

  public void testSynchronizedMethod() {
    doTest();
    checkQuickFixAll();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final SynchronizedMethodInspection inspection = new SynchronizedMethodInspection();
    inspection.m_includeNativeMethods = false;
    inspection.ignoreSynchronizedSuperMethods = true;
    return inspection;
  }
}