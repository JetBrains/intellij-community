// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.internationalization;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.application.PathManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class ImplicitDefaultCharsetUsageInspectionFixTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected String getBasePath() {
    return "/com/siyeh/igtest/internationalization/implicit_default_charset_usage";
  }

  @Override
  protected @NotNull String getTestDataPath() {
    return PathManager.getCommunityHomePath() + "/java/java-tests/testData/ig";
  }

  @Override
  protected LanguageLevel getLanguageLevel() {
    LanguageLevel level = super.getLanguageLevel();
    return level != getDefaultLanguageLevel() ? level : LanguageLevel.JDK_1_9;
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return super.getLanguageLevel() != getDefaultLanguageLevel()
           ? LightJavaCodeInsightFixtureTestCase.JAVA_11
           : LightJavaCodeInsightFixtureTestCase.JAVA_9;
  }

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[] {new ImplicitDefaultCharsetUsageInspection()};
  }
}