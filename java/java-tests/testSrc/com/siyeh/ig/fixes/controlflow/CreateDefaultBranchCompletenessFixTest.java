// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.controlflow;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.ig.LightJavaInspectionTestCase;
import com.siyeh.ig.controlflow.SwitchStatementsWithoutDefaultInspection;
import org.jetbrains.annotations.NotNull;

public class CreateDefaultBranchCompletenessFixTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new SwitchStatementsWithoutDefaultInspection[]{new SwitchStatementsWithoutDefaultInspection()};
  }

  @Override
  protected String getBasePath() {
    return "/com/siyeh/igfixes/controlflow/create_default_completeness";
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LightJavaCodeInsightFixtureTestCase.JAVA_20;
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + LightJavaInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH;
  }
}
