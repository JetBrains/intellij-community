// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

import static com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_21_ANNOTATED;

public class CreateLocalFromUsageTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createLocalFromUsage";
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21_ANNOTATED;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    JavaCodeStyleSettings.getInstance(getProject()).GENERATE_FINAL_LOCALS = getTestName(true).contains("final");
  }
}
