// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase5;
import com.intellij.codeInspection.dataFlow.DataFlowInspection;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;

public class ReplaceWithOfNullableFixTest extends LightQuickFixParameterizedTestCase5 {
  
  @BeforeEach
  void setupInspections() {
    getFixture().enableInspections(DataFlowInspection.class);
    if (getTestNameRule().getDisplayName().contains("Guava")) {
      ReplaceFromOfNullableFixTest.addGuavaOptional(getFixture());
    }
  }
 
  @Override
  protected @NotNull String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/replaceWithOfNullable";
  }
}