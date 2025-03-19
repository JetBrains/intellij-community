// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class LoopStatementsThatDontLoopInspectionTest extends LightJavaInspectionTestCase {

  public void testLoopStatementsThatDontLoop() {
    doTest();
  }

  public void testIgnoreForeach() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final LoopStatementsThatDontLoopInspection inspection = new LoopStatementsThatDontLoopInspection();
    inspection.ignoreForeach = getTestName(false).equals("IgnoreForeach");
    return inspection;
  }
}
