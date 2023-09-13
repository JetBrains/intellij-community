// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase5;
import org.jetbrains.annotations.NotNull;

import static com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_20;

public class RemoveEnumConstantQualifierFixTest extends LightQuickFixParameterizedTestCase5 {
  public RemoveEnumConstantQualifierFixTest() {
    super(JAVA_20);
  }

  @NotNull
  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/removeEnumConstantQualifier";
  }
}
