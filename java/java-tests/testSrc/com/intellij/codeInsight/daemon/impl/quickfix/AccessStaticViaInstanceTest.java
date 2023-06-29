// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.accessStaticViaInstance.AccessStaticViaInstance;
import com.intellij.ui.ChooserInterceptor;
import com.intellij.ui.UiInterceptors;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AccessStaticViaInstanceTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    if (getTestName(false).endsWith("SideEffect.java")) {
      UiInterceptors.register(new ChooserInterceptor(List.of("Delete possible side effects", "Extract possible side effects"), 
                                                     "Extract possible side effects"));
    }
  }

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[] {new AccessStaticViaInstance()};
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/accessStaticViaInstance";
  }

}
