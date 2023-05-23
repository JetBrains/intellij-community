// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.RedundantStreamOptionalCallInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class RedundantStreamOptionalCallInspectionTest extends LightJavaInspectionTestCase {
  public static final String TEST_DATA_DIR = "/inspection/redundantStreamOptionalCall/";

  public void testRedundantStreamOptionalCall() {
    doTest();
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return new RedundantStreamOptionalCallInspection();
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + TEST_DATA_DIR;
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_17;
  }

  public static class RedundantStreamOptionalCallFixTest extends LightQuickFixParameterizedTestCase {
    @Override
    protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
      return new LocalInspectionTool[]{new RedundantStreamOptionalCallInspection()};
    }

    @Override
    protected String getBasePath() {
      return TEST_DATA_DIR;
    }
  }
}
