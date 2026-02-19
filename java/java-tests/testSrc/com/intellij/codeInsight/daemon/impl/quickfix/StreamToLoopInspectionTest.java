// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.streamToLoop.StreamToLoopInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

import static com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_21;


public class StreamToLoopInspectionTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    StreamToLoopInspection inspection = new StreamToLoopInspection();
    inspection.SUPPORT_UNKNOWN_SOURCES = true;
    return new LocalInspectionTool[]{inspection};
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/streamToLoop";
  }
}