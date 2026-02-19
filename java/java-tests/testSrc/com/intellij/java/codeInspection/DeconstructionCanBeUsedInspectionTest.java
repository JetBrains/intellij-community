// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.DeconstructionCanBeUsedInspection;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class DeconstructionCanBeUsedInspectionTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new DeconstructionCanBeUsedInspection()};
  }

  @Override
  protected String getBasePath() {
    return "/inspection/deconstructionCanBeUsed";
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LightJavaCodeInsightFixtureTestCase.JAVA_21;
  }

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_21;
  }
}
