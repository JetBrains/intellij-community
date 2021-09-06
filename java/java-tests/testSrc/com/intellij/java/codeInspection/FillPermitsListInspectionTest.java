// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.FillPermitsListInspection;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

public class FillPermitsListInspectionTest extends LightQuickFixParameterizedTestCase {

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new FillPermitsListInspection()};
  }

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_16_PREVIEW;
  }

  @Override
  protected String getBasePath() {
    return "/inspection/fillPermitsList";
  }
}
