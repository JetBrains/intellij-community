// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.TextBlockMigrationInspection;
import org.jetbrains.annotations.NotNull;

/**
 * @see TextBlockMigrationInspection
 */
public class TextBlockMigrationInspectionTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    TextBlockMigrationInspection inspection = new TextBlockMigrationInspection();
    inspection.mySuggestLiteralReplacement = true;
    return new LocalInspectionTool[]{inspection};
  }

  @Override
  protected String getBasePath() {
    return "/inspection/textBlockMigration/";
  }

}
