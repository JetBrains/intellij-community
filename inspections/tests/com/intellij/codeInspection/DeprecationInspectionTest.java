/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection;

import com.intellij.codeInspection.deprecation.DeprecationInspection;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;

/**
 * @author max
 */
public class DeprecationInspectionTest extends InspectionTestCase {
  private void doTest() throws Exception {
    doTest("deprecation/" + getTestName(false), new LocalInspectionToolWrapper(new DeprecationInspection()));
  }

  public void testDeprecatedMethod() throws Exception{
    doTest();
  }

  public void testDeprecatedInner() throws Exception {
    doTest();
  }

  public void testDeprecatedField() throws Exception{
    doTest();
  }
}
