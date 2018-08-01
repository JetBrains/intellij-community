// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.duplicateThrows.DuplicateThrowsInspection;
import org.jetbrains.annotations.NotNull;

public class DuplicateThrowsInspectionFixTest extends LightQuickFixParameterizedTestCase {

  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new DuplicateThrowsInspection()};
  }

  public void test() { doAllTests(); }

  @Override
  protected String getBasePath() {
    return "/inspection/duplicateThrows/quickFix";
  }
}
