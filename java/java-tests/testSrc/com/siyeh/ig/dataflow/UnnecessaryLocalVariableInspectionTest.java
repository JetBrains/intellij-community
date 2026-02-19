// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.dataflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;

public class UnnecessaryLocalVariableInspectionTest extends LightJavaInspectionTestCase {
  @Override
  protected InspectionProfileEntry getInspection() {
    return new UnnecessaryLocalVariableInspection();
  }

  public void testUnnecessaryLocalVariable() { doTest(); }
  
  public void testCastNecessary() { doTest(); }

  public void testTree() { doTest(); }

  public void testSwitchExpression() { doTest(); }

  public void testEffectivelyFinalVariableInGuard() { doTest(); }

  public void testUnknownExpression() { doTest(); }
}