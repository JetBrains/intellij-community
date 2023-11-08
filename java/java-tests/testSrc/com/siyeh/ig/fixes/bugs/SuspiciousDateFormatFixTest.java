// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.fixes.bugs;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import com.siyeh.ig.bugs.SuspiciousDateFormatInspection;
import org.jetbrains.annotations.NotNull;

import static com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_21;

public class SuspiciousDateFormatFixTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new SuspiciousDateFormatInspection()};
  }

  @Override
  protected String getBasePath() {
    return "/com/siyeh/igfixes/bugs/suspicious_date_format";
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getCommunityHomePath() + LightJavaInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH;
  }
}
