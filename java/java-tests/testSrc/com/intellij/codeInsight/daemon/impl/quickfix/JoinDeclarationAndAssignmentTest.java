// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.JoinDeclarationAndAssignmentJavaInspection;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.ui.ChooserInterceptor;
import com.intellij.ui.UiInterceptors;
import org.jetbrains.annotations.NotNull;

public class JoinDeclarationAndAssignmentTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    String name = getTestName(false);
    if (name.endsWith("SideEffect.java")) {
      UiInterceptors.register(new ChooserInterceptor(null, "Extract side effect.*"));
    }
  }


  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new JoinDeclarationAndAssignmentJavaInspection()};
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/joinDeclaration";
  }
}
