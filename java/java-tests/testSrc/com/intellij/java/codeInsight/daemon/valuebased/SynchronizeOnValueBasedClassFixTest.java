// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.valuebased;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.valuebased.SynchronizeOnValueBasedClassInspection;
import com.intellij.lang.annotation.HighlightSeverity;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class SynchronizeOnValueBasedClassFixTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    @Language("JAVA")
    String valueBased = """
      package jdk.internal;
      public @interface ValueBased {
      }
      """;
    createAndSaveFile("jdk/internal/ValueBased.java", valueBased);
  }

  @Override
  protected @NonNls String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/valuebased/quickfix";
  }

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{ new SynchronizeOnValueBasedClassInspection() };
  }

  @Override
  protected void beforeActionStarted(String testName, String contents) {
    assertEmpty(doHighlighting(HighlightSeverity.ERROR));
  }
}
