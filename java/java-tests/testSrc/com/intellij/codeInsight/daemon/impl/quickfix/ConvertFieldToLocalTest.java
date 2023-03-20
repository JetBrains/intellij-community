// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.varScopeCanBeNarrowed.FieldCanBeLocalInspection;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import org.jetbrains.annotations.NotNull;


public class ConvertFieldToLocalTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/convert2Local";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(getProject());
    settings.GENERATE_FINAL_LOCALS = StringUtil.containsIgnoreCase(getTestName(true), "final");
  }

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    final FieldCanBeLocalInspection inspection = new FieldCanBeLocalInspection();
    inspection.IGNORE_FIELDS_USED_IN_MULTIPLE_METHODS = false;
    return new LocalInspectionTool[] { inspection };
  }

}