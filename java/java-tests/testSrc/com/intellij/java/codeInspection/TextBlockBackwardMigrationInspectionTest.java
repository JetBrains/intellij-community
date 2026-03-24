// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.TextBlockBackwardMigrationInspection;
import org.jetbrains.annotations.NotNull;

/**
 * @see TextBlockBackwardMigrationInspection
 */
public class TextBlockBackwardMigrationInspectionTest extends LightQuickFixParameterizedTestCase {

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new TextBlockBackwardMigrationInspection()};
  }

  @Override
  protected String getBasePath() {
    return "/inspection/textBlockBackwardMigration/";
  }
}
