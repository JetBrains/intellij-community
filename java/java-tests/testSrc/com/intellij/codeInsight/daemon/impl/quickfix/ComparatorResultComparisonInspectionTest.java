// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.ComparatorResultComparisonInspection;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.LocalInspectionTool;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;


public class ComparatorResultComparisonInspectionTest extends LightJavaInspectionTestCase {
  static final String TEST_DATA_DIR = "/codeInsight/daemonCodeAnalyzer/quickFix/comparatorResultComparison/";

  public void testComparatorResultComparison() {
    doTest();
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new ComparatorResultComparisonInspection();
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + TEST_DATA_DIR;
  }

  public static class ComparatorResultComparisonInspectionFixTest extends LightQuickFixParameterizedTestCase {
    @Override
    protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
      return new LocalInspectionTool[]{new ComparatorResultComparisonInspection()};
    }

    @Override
    protected String getBasePath() {
      return TEST_DATA_DIR;
    }
  }
}
