// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;

public class CreateMethodFromMethodReferenceFixTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createMethodFromMethodRef";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    if (getTestName(false).endsWith("Final.java")) {
      JavaCodeStyleSettings.getInstance(getProject()).GENERATE_FINAL_PARAMETERS = true;
    }
  }
}