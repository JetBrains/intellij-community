// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.suspiciousNameCombination.SuspiciousNameCombinationInspection;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;


public class SuspiciousNameCombinationTest extends LightJavaInspectionTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/suspiciousNameCombination";
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    SuspiciousNameCombinationInspection inspection = new SuspiciousNameCombinationInspection();
    inspection.addNameGroup("someWord,otherWord");
    return inspection;
  }

  public void testAssignment() { doTest();}
  public void testInitializer() { doTest();}
  public void testParameter() { doTest();}
  public void testReturnValue() { doTest();}
  public void testExcluded() { doTest();}
  public void testTwoWords() { doTest();}
}
