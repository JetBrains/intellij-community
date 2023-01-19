// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.ig.LightJavaInspectionTestCase;
import com.siyeh.ig.redundancy.RedundantStringOperationInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RedundantStringOperationInspectionTest extends LightJavaInspectionTestCase {
  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new RedundantStringOperationInspection();
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LightJavaCodeInsightFixtureTestCase.JAVA_9;
  }

  public void testEmptyStringArgument() {doTest();}
  public void testStringLengthArgument() {doTest();}
  public void testZeroArgument() {doTest();}
  public void testBAOStoString() {doTest();}
  public void testNewStringNewChar() {doTest();}
  public void testStringValueOfNewChar() {doTest();}
  public void testRedundantStringOperation() {doTest();}

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/redundantStringOperation/";
  }
}