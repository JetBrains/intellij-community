// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.ig.LightJavaInspectionTestCase;
import com.siyeh.ig.redundancy.OnlyOneElementUsedInspection;
import com.siyeh.ig.redundancy.RedundantStringOperationInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class OnlyOneElementUsedInspectionTest extends LightJavaInspectionTestCase {
  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new OnlyOneElementUsedInspection();
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LightJavaCodeInsightFixtureTestCase.JAVA_11;
  }

  public void testOnlyOneElementUsed() {doTest();}

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/onlyOneElementUsed/";
  }
}