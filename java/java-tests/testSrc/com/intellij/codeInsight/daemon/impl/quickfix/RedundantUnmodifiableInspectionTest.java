// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.RedundantUnmodifiableInspection;
import org.jetbrains.annotations.NotNull;


public class RedundantUnmodifiableInspectionTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new RedundantUnmodifiableInspection()
    };
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/redundantUnmodifiable";
  }
}
