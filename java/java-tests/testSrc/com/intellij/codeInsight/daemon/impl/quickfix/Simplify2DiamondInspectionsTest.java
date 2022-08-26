// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.ExplicitTypeCanBeDiamondInspection;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.annotations.NotNull;


//todo test3 should be checked if it compiles - as now javac infers Object instead of String?!
public class Simplify2DiamondInspectionsTest extends LightQuickFixParameterizedTestCase {

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new ExplicitTypeCanBeDiamondInspection(),
    };
  }

  private boolean myAlignment;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    CommonCodeStyleSettings settings = getSettings();
    myAlignment = settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS;
    settings.ALIGN_MULTILINE_PARAMETERS_IN_CALLS = true;
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      getSettings().ALIGN_MULTILINE_PARAMETERS_IN_CALLS = myAlignment;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  private CommonCodeStyleSettings getSettings() {
    return CodeStyle.getSettings(getProject()).getCommonSettings(JavaLanguage.INSTANCE);
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/explicit2diamond";
  }

  @Override
  protected LanguageLevel getDefaultLanguageLevel() {
    return LanguageLevel.JDK_1_8;
  }
}