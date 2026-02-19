// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.lambdaToExplicit.ExplicitArgumentCanBeLambdaInspection;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NotNull;


public class ExplicitArgumentCanBeLambdaInspectionTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new ExplicitArgumentCanBeLambdaInspection()
    };
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk9();
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/explicitArgumentCanBeLambda";
  }
}