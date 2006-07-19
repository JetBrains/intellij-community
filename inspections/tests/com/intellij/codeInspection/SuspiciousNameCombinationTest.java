/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection;

import com.intellij.codeInspection.suspiciousNameCombination.SuspiciousNameCombinationInspection;
import com.intellij.testFramework.InspectionTestCase;

/**
 * @author yole
 */
public class SuspiciousNameCombinationTest extends InspectionTestCase {
  private void doTest() throws Exception {
    doTest("suspiciousNameCombination/" + getTestName(true), new SuspiciousNameCombinationInspection());
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
