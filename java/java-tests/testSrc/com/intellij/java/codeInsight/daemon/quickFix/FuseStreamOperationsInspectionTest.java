// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.streamMigration.FuseStreamOperationsInspection;
import org.jetbrains.annotations.NotNull;


public class FuseStreamOperationsInspectionTest extends LightQuickFixParameterizedTestCase {
  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    FuseStreamOperationsInspection inspection = new FuseStreamOperationsInspection();
    inspection.myStrictMode = getTestName(false).contains("Strict");
    return new LocalInspectionTool[]{inspection};
  }

  public void test() { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/fuseStreamOperations";
  }
}