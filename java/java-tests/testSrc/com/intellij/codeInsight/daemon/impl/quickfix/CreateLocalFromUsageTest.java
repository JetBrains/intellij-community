// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;

public class CreateLocalFromUsageTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createLocalFromUsage";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setLanguageLevel(LanguageLevel.JDK_21);
    JavaCodeStyleSettings.getInstance(getProject()).GENERATE_FINAL_LOCALS = getTestName(true).contains("final");
  }
}
