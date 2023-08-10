// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase5;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;

public class AddExceptionToCatchTest extends LightQuickFixParameterizedTestCase5 {

  @BeforeEach
  public void setUp() {
    getFixture().setCaresAboutInjection(false);
  }
  
  @Override
  protected @NotNull String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/addCatchBlock";
  }
}
