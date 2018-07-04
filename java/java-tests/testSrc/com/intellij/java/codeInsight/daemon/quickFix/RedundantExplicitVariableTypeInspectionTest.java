// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.RedundantExplicitVariableTypeInspection;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;


public class RedundantExplicitVariableTypeInspectionTest extends LightQuickFixParameterizedTestCase {
  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new RedundantExplicitVariableTypeInspection(),
    };
  }

  public void test() { doAllTests(); }

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_10;
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/explicit2var";
  }
}