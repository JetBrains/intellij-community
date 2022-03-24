// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.defUse.DefUseInspection;
import com.intellij.codeInspection.sillyAssignment.SillyAssignmentInspection;
import org.jetbrains.annotations.NotNull;

public class RemoveUnusedAssignmentTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[] {new DefUseInspection(), new SillyAssignmentInspection()};
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/unusedAssignment";
  }
}
