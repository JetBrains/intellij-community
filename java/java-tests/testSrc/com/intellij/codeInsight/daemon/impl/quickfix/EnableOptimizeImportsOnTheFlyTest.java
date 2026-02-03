// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightWorkspaceSettings;
import com.intellij.codeInsight.daemon.quickFix.ActionHint;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.unusedImport.UnusedImportInspection;
import org.jetbrains.annotations.NotNull;

public class EnableOptimizeImportsOnTheFlyTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTool(new UnusedImportInspection());
    enableInspectionTool(new UnusedDeclarationInspection());
  }

  @Override
  protected void doAction(@NotNull final ActionHint actionHint, @NotNull final String testFullPath, @NotNull final String testName) {
    CodeInsightWorkspaceSettings.getInstance(getProject()).setOptimizeImportsOnTheFly(false, getTestRootDisposable());
    IntentionAction action = findActionAndCheck(actionHint, testFullPath);
    if (action != null) {
      action.invoke(getProject(), getEditor(), getFile());
      assertTrue(CodeInsightWorkspaceSettings.getInstance(getProject()).isOptimizeImportsOnTheFly());
    }
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/enableOptimizeImportsOnTheFly";
  }
}

