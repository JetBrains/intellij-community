// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.valuebased;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.valuebased.SynchronizeOnValueBasedClassInspection;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class SynchronizeOnValueBasedClassFixTest extends LightQuickFixParameterizedTestCase {

  @Override
  protected @NonNls String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/valuebased/quickfix";
  }

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{ new SynchronizeOnValueBasedClassInspection() };
  }

}
