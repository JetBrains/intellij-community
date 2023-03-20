// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.java18api.Java8ListReplaceAllInspection;
import org.jetbrains.annotations.NotNull;

public class Java8ListReplaceAllInspectionTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    Java8ListReplaceAllInspection inspection = new Java8ListReplaceAllInspection();
    inspection.dontWarnInCaseOfMultilineLambda = false;
    return new LocalInspectionTool[]{inspection};
  }

  @Override
  protected String getBasePath() {
    return "/inspection/java8ListReplaceAll";
  }
}
