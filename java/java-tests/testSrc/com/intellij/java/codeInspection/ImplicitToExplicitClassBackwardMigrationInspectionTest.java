// Copyright 2000-2023 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.ImplicitToExplicitClassBackwardMigrationInspection;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

public class ImplicitToExplicitClassBackwardMigrationInspectionTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new ImplicitToExplicitClassBackwardMigrationInspection()};
  }

  @Override
  protected String getBasePath() {
    return "/inspection/implicitToExplicitClassBackwardMigration/";
  }

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_22_PREVIEW;
  }
}