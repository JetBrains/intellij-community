// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixParameterizedTestCase;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class CreateMissingBooleanPrimitiveBranchesFixTest extends LightQuickFixParameterizedTestCase {
  @Override
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/createMissingBooleanPrimitiveBranches";
  }

  @Override
  protected LanguageLevel getLanguageLevel() {
    return JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.getMinimumLevel();
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LightJavaCodeInsightFixtureTestCase.JAVA_23;
  }
}
