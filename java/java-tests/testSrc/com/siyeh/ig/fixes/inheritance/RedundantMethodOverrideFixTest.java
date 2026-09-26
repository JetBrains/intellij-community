// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.inheritance;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.siyeh.ig.LightJavaInspectionTestCase;
import com.siyeh.ig.inheritance.RedundantMethodOverrideInspection;
import org.jetbrains.annotations.NotNull;

public class RedundantMethodOverrideFixTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new RedundantMethodOverrideInspection[]{ new RedundantMethodOverrideInspection() };
  }

  @Override
  protected String getBasePath() {
    return "/com/siyeh/igfixes/inheritance/redundant_override";
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + LightJavaInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH;
  }
}
