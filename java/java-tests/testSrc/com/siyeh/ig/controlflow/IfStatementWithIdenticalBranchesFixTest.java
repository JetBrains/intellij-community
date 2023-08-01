// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.controlflow;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import org.jetbrains.annotations.NotNull;

public class IfStatementWithIdenticalBranchesFixTest extends LightQuickFixParameterizedTestCase {

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    IfStatementWithIdenticalBranchesInspection inspection = new IfStatementWithIdenticalBranchesInspection();
    inspection.myHighlightWhenLastStatementIsCall = !getTestName(false).equals("LastStatementIsCallInfo.java");
    return new LocalInspectionTool[]{inspection};
  }

  @Override
  protected String getBasePath() {
    return "/inspection/commonIfParts";
  }
}