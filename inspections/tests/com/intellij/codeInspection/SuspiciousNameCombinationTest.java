/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection;

import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.suspiciousNameCombination.SuspiciousNameCombinationInspection;

/**
 * @author yole
 */
public class SuspiciousNameCombinationTest extends InspectionTestCase {
  private void doTest() throws Exception {
    final SuspiciousNameCombinationInspection tool = new SuspiciousNameCombinationInspection();
    LocalInspectionToolWrapper wrapper = new LocalInspectionToolWrapper(tool);
    doTest("suspiciousNameCombination/" + getTestName(false), wrapper);
  }

  public void testAssignment() throws Exception {
    doTest();
  }

  public void testInitializer() throws Exception {
    doTest();
  }

  public void testParameter() throws Exception {
    doTest();
  }
}
