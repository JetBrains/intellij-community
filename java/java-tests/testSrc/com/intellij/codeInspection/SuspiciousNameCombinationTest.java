/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.suspiciousNameCombination.SuspiciousNameCombinationInspection;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class SuspiciousNameCombinationTest extends LightInspectionTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/suspiciousNameCombination";
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new SuspiciousNameCombinationInspection();
  }

  public void testAssignment() throws Exception { doTest();}
  public void testInitializer() throws Exception { doTest();}
  public void testParameter() throws Exception { doTest();}
  public void testReturnValue() throws Exception { doTest();}
  public void testExcluded() throws Exception { doTest();}
}
