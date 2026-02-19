// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.dataFlow.ConstantValueInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import org.jetbrains.annotations.NotNull;

import static com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_23;

/**
 * @see UnwrapIfStatementFixTest -- more tests for the same quickfix
 */
public class SimplifyBooleanExpressionFixTest extends LightQuickFixParameterizedTestCase {

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new ConstantValueInspection()};
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_23;
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/simplifyBooleanExpressionFix";
  }
}