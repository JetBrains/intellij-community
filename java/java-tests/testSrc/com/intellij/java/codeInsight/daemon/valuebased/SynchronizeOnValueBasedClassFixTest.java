// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.valuebased;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.valuebased.SynchronizeOnValueBasedClassInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_16;

public class SynchronizeOnValueBasedClassFixTest extends LightQuickFixParameterizedTestCase {

  @Override
  protected @NonNls String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/valuebased/quickfix";
  }

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{ new SynchronizeOnValueBasedClassInspection() };
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_16;
  }
}
