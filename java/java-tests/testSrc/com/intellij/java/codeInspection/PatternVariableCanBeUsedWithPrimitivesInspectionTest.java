// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.PatternVariableCanBeUsedInspection;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class PatternVariableCanBeUsedWithPrimitivesInspectionTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    PatternVariableCanBeUsedInspection inspection = new PatternVariableCanBeUsedInspection();
    inspection.reportAlsoCastWithIntroducingNewVariable = true;
    return new LocalInspectionTool[]{inspection};
  }

  @Override
  protected LanguageLevel getLanguageLevel() {
    return JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.getMinimumLevel();
  }

  @Override
  protected String getBasePath() {
    return "/inspection/patternVariableCanBeUsedWithPrimitives";
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LightJavaCodeInsightFixtureTestCase.JAVA_X;
  }
}
