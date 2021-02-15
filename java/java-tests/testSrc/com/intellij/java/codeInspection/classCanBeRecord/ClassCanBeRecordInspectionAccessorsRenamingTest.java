// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection.classCanBeRecord;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.classCanBeRecord.ClassCanBeRecordInspection;
import com.intellij.codeInspection.classCanBeRecord.ClassCanBeRecordInspection.ConversionStrategy;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class ClassCanBeRecordInspectionAccessorsRenamingTest extends LightQuickFixParameterizedTestCase {

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new ClassCanBeRecordInspection(ConversionStrategy.SILENTLY, false)};
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LightJavaCodeInsightFixtureTestCase.JAVA_15;
  }

  @Override
  protected String getBasePath() {
    return "/inspection/classCanBeRecord/accessorsRenaming";
  }
}
