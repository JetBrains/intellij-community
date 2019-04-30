// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.ExplicitMinMaxCheckInspection;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @see com.intellij.codeInspection.ExplicitMinMaxCheckInspection
 */
public class ExplicitMinMaxCheckInspectionTest extends LightQuickFixParameterizedTestCase {
  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new ExplicitMinMaxCheckInspection()};
  }

  @Override
  protected String getBasePath() {
    return "/inspection/explicitMinMaxCheck";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return LightCodeInsightFixtureTestCase.JAVA_12;
  }
}
