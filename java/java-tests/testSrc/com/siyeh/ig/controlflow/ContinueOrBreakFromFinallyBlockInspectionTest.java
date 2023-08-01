// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import com.siyeh.ig.errorhandling.ContinueOrBreakFromFinallyBlockInspection;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class ContinueOrBreakFromFinallyBlockInspectionTest extends LightJavaInspectionTestCase {

  public void testContinueOrBreakFromFinallyBlock() {
    doTest();
  }

  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new ContinueOrBreakFromFinallyBlockInspection();
  }
}
