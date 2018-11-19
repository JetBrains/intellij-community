// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.java.codeInsight.daemon.quickFix;

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
