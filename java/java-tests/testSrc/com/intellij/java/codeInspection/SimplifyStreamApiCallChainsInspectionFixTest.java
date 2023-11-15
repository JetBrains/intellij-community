// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.SimplifyStreamApiCallChainsInspection;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NotNull;

public class SimplifyStreamApiCallChainsInspectionFixTest extends LightQuickFixParameterizedTestCase {

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new SimplifyStreamApiCallChainsInspection()};
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk11();
  }

  @Override
  protected String getBasePath() {
    return "/inspection/streamApiCallChains";
  }
}
