// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.EnhancedSwitchBackwardMigrationInspection;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

public class EnhancedSwitchBackwardMigrationInspectionTest extends LightQuickFixParameterizedTestCase {
  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new EnhancedSwitchBackwardMigrationInspection()
    };
  }

  @Override
  protected String getBasePath() {
    return "/inspection/switchExpressionBackwardMigration/";
  }

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_12_PREVIEW;
  }
}
