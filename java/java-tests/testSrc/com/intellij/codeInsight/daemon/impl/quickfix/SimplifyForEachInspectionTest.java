// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;


import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.streamMigration.SimplifyForEachInspection;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

public class SimplifyForEachInspectionTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new SimplifyForEachInspection()};
  }

  @Override
  protected LanguageLevel getDefaultLanguageLevel() {
    return LanguageLevel.JDK_1_8;
  }



  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/simplifyForEach";
  }
}
