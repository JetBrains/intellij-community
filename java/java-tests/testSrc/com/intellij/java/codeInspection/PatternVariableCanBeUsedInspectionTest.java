// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.PatternVariableCanBeUsedInspection;
import org.jetbrains.annotations.NotNull;

public class PatternVariableCanBeUsedInspectionTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new PatternVariableCanBeUsedInspection()};
  }
  
  @Override
  protected String getBasePath() {
    return "/inspection/patternVariableCanBeUsed";
  }
}
