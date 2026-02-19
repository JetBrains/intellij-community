// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.localCanBeFinal.LocalCanBeFinal;
import com.intellij.codeInspection.varScopeCanBeNarrowed.FieldCanBeLocalInspection;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;

public class ImplicitClassSuppressLocalInspectionTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected LanguageLevel getLanguageLevel() {
    return JavaFeature.IMPLICIT_CLASSES.getStandardLevel();
  }

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new LocalCanBeFinal(), new FieldCanBeLocalInspection()};
  }

  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/suppressImplicitClassLocalInspection";
  }
}

