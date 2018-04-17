// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.SuspiciousListRemoveInLoopInspection;
import com.siyeh.ig.LightInspectionTestCase;

public class SuspiciousListRemoveInLoopInspectionTest extends LightInspectionTestCase {
  public void testListRemoveInLoop() {
    doTest();
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new SuspiciousListRemoveInLoopInspection();
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/listRemoveInLoop/";
  }
}
