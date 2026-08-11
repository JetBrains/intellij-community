// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.codeInspection;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase5;
import com.intellij.codeInspection.nullable.NotNullFieldNotInitializedInspection;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;

public class NotNullFieldNotInitializedFixesTest extends LightQuickFixParameterizedTestCase5 {

  @BeforeEach
  public void setupInspections() {
    JavaCodeInsightTestFixture fixture = getFixture();
    fixture.enableInspections(NotNullFieldNotInitializedInspection.class);
    DataFlowInspectionTestCase.addJSpecifyNullMarked(fixture);
    DataFlowInspectionTestCase.setupTypeUseAnnotations("org.jspecify.annotations", fixture);
  }

  @Override
  protected @NotNull String getBasePath() {
    return "/inspection/notNullField/quickFix";
  }
}
