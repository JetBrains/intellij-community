// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.StringRepeatCanBeUsedInspection;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NotNull;

public class StringRepeatCanBeUsedInspectionTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected LanguageLevel getDefaultLanguageLevel() {
    return LanguageLevel.JDK_11;
  }

  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new StringRepeatCanBeUsedInspection()};
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk9();
  }

  @Override
  protected String getBasePath() {
    return "/inspection/stringRepeat";
  }
}
