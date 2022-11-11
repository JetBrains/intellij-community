// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;

public class RemoveRedundantCastTest extends LightQuickFixParameterizedTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    enableInspectionTool(new RedundantCastInspection());
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/redundantCast";
  }
}
