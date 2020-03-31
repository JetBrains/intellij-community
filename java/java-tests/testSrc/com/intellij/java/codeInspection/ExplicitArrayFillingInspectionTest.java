// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.ExplicitArrayFillingInspection;
import com.intellij.codeInspection.LocalInspectionTool;
import org.jetbrains.annotations.NotNull;

public class ExplicitArrayFillingInspectionTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new ExplicitArrayFillingInspection()};
  }

  @Override
  protected String getBasePath() {
    return "/inspection/explicitArrayFilling";
  }
}
