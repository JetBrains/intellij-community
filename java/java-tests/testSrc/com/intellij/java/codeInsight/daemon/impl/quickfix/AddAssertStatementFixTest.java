// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase5;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;

public class AddAssertStatementFixTest extends LightQuickFixParameterizedTestCase5 {
  @BeforeEach
  void setupInspections() {
    getFixture().enableInspections(DataFlowInspection.class);
  }
  
  @Override
  public @NotNull String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/addAssert";
  }
}