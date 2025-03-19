// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.controlflow;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.openapi.application.PathManager;
import org.jetbrains.annotations.NotNull;

public class NonStrictComparisonCanBeEqualityInspectionFixTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTool(new NonStrictComparisonCanBeEqualityInspection());
  }

  @Override
  protected String getBasePath() {
    return "/com/siyeh/igtest/controlflow/non_strict_comparison";
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PathManager.getCommunityHomePath() + "/java/java-tests/testData/ig";
  }
}
