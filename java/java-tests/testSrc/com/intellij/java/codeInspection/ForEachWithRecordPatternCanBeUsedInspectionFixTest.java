// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.ForEachWithRecordPatternCanBeUsedInspection;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class ForEachWithRecordPatternCanBeUsedInspectionFixTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LightJavaCodeInsightFixtureTestCase.JAVA_20;
  }

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_20_PREVIEW;
  }

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new ForEachWithRecordPatternCanBeUsedInspection()};
  }

  @Override
  protected String getBasePath() {
    return "/inspection/forEachWithRecordPatternCanBeUsedFix/";
  }
}
